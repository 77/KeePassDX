/*
 * Copyright 2017 Brian Pellin, Jeremy Jamet / Kunzisoft.
 *
 * This file is part of KeePass DX.
 *
 *  KeePass DX is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  KeePass DX is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with KeePass DX.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.kunzisoft.keepass.fileselect;

import android.Manifest;
import android.app.Activity;
import android.app.assist.AssistStructure;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.kunzisoft.keepass.R;
import com.kunzisoft.keepass.activities.EntrySelectionHelper;
import com.kunzisoft.keepass.activities.GroupActivity;
import com.kunzisoft.keepass.autofill.AutofillHelper;
import com.kunzisoft.keepass.database.action.AssignPasswordInDatabaseRunnable;
import com.kunzisoft.keepass.database.action.CreateDatabaseRunnable;
import com.kunzisoft.keepass.database.action.ProgressDialogRunnable;
import com.kunzisoft.keepass.dialogs.AssignMasterKeyDialogFragment;
import com.kunzisoft.keepass.dialogs.CreateFileDialogFragment;
import com.kunzisoft.keepass.education.FileDatabaseSelectActivityEducation;
import com.kunzisoft.keepass.fileselect.database.FileDatabaseHistory;
import com.kunzisoft.keepass.magikeyboard.KeyboardHelper;
import com.kunzisoft.keepass.password.PasswordActivity;
import com.kunzisoft.keepass.settings.PreferencesUtil;
import com.kunzisoft.keepass.stylish.StylishActivity;
import com.kunzisoft.keepass.tasks.ActionRunnable;
import com.kunzisoft.keepass.utils.EmptyUtils;
import com.kunzisoft.keepass.utils.MenuUtil;
import com.kunzisoft.keepass.utils.UriUtil;

import net.cachapa.expandablelayout.ExpandableLayout;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URLDecoder;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class FileDatabaseSelectActivity extends StylishActivity implements
        CreateFileDialogFragment.DefinePathDialogListener,
        AssignMasterKeyDialogFragment.AssignPasswordDialogListener,
        FileDatabaseHistoryAdapter.FileItemOpenListener,
        FileDatabaseHistoryAdapter.FileSelectClearListener,
        FileDatabaseHistoryAdapter.FileInformationShowListener {

    private static final String TAG = "FileDbSelectActivity";

    private static final String EXTRA_STAY = "EXTRA_STAY";

    private FileDatabaseHistoryAdapter mAdapter;
    private View fileListContainer;
    private View createButtonView;
    private View browseButtonView;
    private View openButtonView;

    private FileDatabaseHistory fileDatabaseHistory;

    private View fileSelectExpandableButton;
    private ExpandableLayout fileSelectExpandable;
    private EditText openFileNameView;

    private Uri databaseUri;

    private KeyFileHelper keyFileHelper;

    private String defaultPath;

    /*
     * -------------------------
     * No Standard Launch, pass by PasswordActivity
     * -------------------------
     */

    /*
     * -------------------------
     * 		Keyboard Launch
     * -------------------------
     */

    public static void launchForKeyboardSelection(Activity activity) {
        KeyboardHelper.INSTANCE.startActivityForKeyboardSelection(activity, new Intent(activity, FileDatabaseSelectActivity.class));
    }

    /*
     * -------------------------
     * 		Autofill Launch
     * -------------------------
     */

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void launchForAutofillResult(Activity activity, @NonNull AssistStructure assistStructure) {
        AutofillHelper.INSTANCE.startActivityForAutofillResult(activity,
                new Intent(activity, FileDatabaseSelectActivity.class),
                assistStructure);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        fileDatabaseHistory = FileDatabaseHistory.Companion.getInstance(new WeakReference<>(getApplicationContext()));

        setContentView(R.layout.file_selection);
        fileListContainer = findViewById(R.id.container_file_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);

        openFileNameView = findViewById(R.id.file_filename);

        // Set the initial value of the filename
        defaultPath = Environment.getExternalStorageDirectory().getAbsolutePath()
                + getString(R.string.database_file_path_default)
                + getString(R.string.database_file_name_default)
                + getString(R.string.database_file_extension_default);
        openFileNameView.setHint(R.string.open_link_database);

        // Button to expand file selection
        fileSelectExpandableButton = findViewById(R.id.file_select_expandable_button);
        fileSelectExpandable = findViewById(R.id.file_select_expandable);
        fileSelectExpandableButton.setOnClickListener(view -> {
            if (fileSelectExpandable.isExpanded())
                fileSelectExpandable.collapse();
            else
                fileSelectExpandable.expand();
        });

        // History list
        RecyclerView mListFiles = findViewById(R.id.file_list);
        mListFiles.setLayoutManager(new LinearLayoutManager(this));

        // Open button
        openButtonView = findViewById(R.id.open_database);
        openButtonView.setOnClickListener(v -> {
            String fileName = openFileNameView.getText().toString();
            if (fileName.isEmpty())
                fileName = defaultPath;
            launchPasswordActivityWithPath(fileName);
        });

        // Create button
        createButtonView = findViewById(R.id.create_database);
        createButtonView .setOnClickListener(v ->
                FileDatabaseSelectActivityPermissionsDispatcher
                        .openCreateFileDialogFragmentWithPermissionCheck(FileDatabaseSelectActivity.this)
        );

        keyFileHelper = new KeyFileHelper(this);
        browseButtonView = findViewById(R.id.browse_button);
        browseButtonView.setOnClickListener(keyFileHelper.getOpenFileOnClickViewListener(
                () -> Uri.parse("file://" + openFileNameView.getText().toString())));

        // Construct adapter with listeners
        mAdapter = new FileDatabaseHistoryAdapter(FileDatabaseSelectActivity.this, fileDatabaseHistory.getDatabaseUriList());
        mAdapter.setOnItemClickListener(this);
        mAdapter.setFileSelectClearListener(this);
        mAdapter.setFileInformationShowListener(this);
        mListFiles.setAdapter(mAdapter);

        // Load default database if not an orientation change
        if (! (savedInstanceState != null
                && savedInstanceState.containsKey(EXTRA_STAY)
                && savedInstanceState.getBoolean(EXTRA_STAY, false)) ) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String fileName = prefs.getString(PasswordActivity.KEY_DEFAULT_FILENAME, "");

            if (fileName.length() > 0) {
                Uri dbUri = UriUtil.parseDefaultFile(fileName);
                String scheme = null;
                if (dbUri != null)
                    scheme = dbUri.getScheme();

                if (!EmptyUtils.INSTANCE.isNullOrEmpty(scheme) && scheme.equalsIgnoreCase("file")) {
                    String path = dbUri.getPath();
                    File db = new File(path);

                    if (db.exists()) {
                        launchPasswordActivityWithPath(path);
                    }
                } else {
                    if (dbUri != null)
                        launchPasswordActivityWithPath(dbUri.toString());
                }
            }
        }

        new Handler().post(() -> performedNextEducation(new FileDatabaseSelectActivityEducation(this)));
    }

    private void performedNextEducation(FileDatabaseSelectActivityEducation fileDatabaseSelectActivityEducation) {
        // If no recent files
        if (!fileDatabaseHistory.hasRecentFiles()
                && fileDatabaseSelectActivityEducation.checkAndPerformedCreateDatabaseEducation(
                    createButtonView,
                    tapTargetView -> {
                        FileDatabaseSelectActivityPermissionsDispatcher
                                .openCreateFileDialogFragmentWithPermissionCheck(FileDatabaseSelectActivity.this);
                        return null;
                    },
                    tapTargetView -> {
                        // But if the user cancel, it can also select a database
                        performedNextEducation(fileDatabaseSelectActivityEducation);
                        return null;
                    })
        );
        else if (fileDatabaseSelectActivityEducation.checkAndPerformedSelectDatabaseEducation(
                browseButtonView,
                tapTargetView -> {
                    keyFileHelper.getOpenFileOnClickViewListener().onClick(tapTargetView);
                    return null;
                },
                tapTargetView -> {
                    fileDatabaseSelectActivityEducation.checkAndPerformedOpenLinkDatabaseEducation(
                            fileSelectExpandableButton,
                            tapTargetView1 -> null,
                            tapTargetView12 -> null
                    );
                    return null;
                }
        ));
    }

    private void fileNoFoundAction(FileNotFoundException e) {
        String error = getString(R.string.file_not_found_content);
        Toast.makeText(FileDatabaseSelectActivity.this,
                error, Toast.LENGTH_LONG).show();
        Log.e(TAG, error, e);
    }

    private void launchPasswordActivity(String fileName, String keyFile) {
        EntrySelectionHelper.INSTANCE.doEntrySelectionAction(getIntent(),
                () -> {
                    try {
                        PasswordActivity.launch(FileDatabaseSelectActivity.this,
                                fileName, keyFile);
                    } catch (FileNotFoundException e) {
                        fileNoFoundAction(e);
                    }
                    return null;
                },
                () -> {
                    try {
                        PasswordActivity.launchForKeyboardResult(FileDatabaseSelectActivity.this,
                                fileName, keyFile);
                        finish();
                    } catch (FileNotFoundException e) {
                        fileNoFoundAction(e);
                    }
                    return null;
                },
                assistStructure -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            PasswordActivity.launchForAutofillResult(FileDatabaseSelectActivity.this,
                                    fileName, keyFile,
                                    assistStructure);
                        } catch (FileNotFoundException e) {
                            fileNoFoundAction(e);
                        }
                    }
                    return null;
                });
    }

    private void launchPasswordActivityWithPath(String path) {
        launchPasswordActivity(path, "");
        // Delete flickering for kitkat <=
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            overridePendingTransition(0, 0);
    }

    private void updateExternalStorageWarning() {
        // To show errors
        int warning = -1;
        String state = Environment.getExternalStorageState();
        if (state.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            warning = R.string.read_only_warning;
        } else if (!state.equals(Environment.MEDIA_MOUNTED)) {
            warning = R.string.warning_unmounted;
        }

        TextView labelWarningView = findViewById(R.id.label_warning);
        if (warning != -1) {
            labelWarningView.setText(warning);
            labelWarningView.setVisibility(View.VISIBLE);
        } else {
            labelWarningView.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateExternalStorageWarning();
        updateFileListVisibility();
        mAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // only to keep the current activity
        outState.putBoolean(EXTRA_STAY, true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // NOTE: delegate the permission handling to generated method
        FileDatabaseSelectActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    public void openCreateFileDialogFragment() {
        CreateFileDialogFragment createFileDialogFragment = new CreateFileDialogFragment();
        createFileDialogFragment.show(getSupportFragmentManager(), "createFileDialogFragment");
    }

    private void updateFileListVisibility() {
        if(mAdapter.getItemCount() == 0)
            fileListContainer.setVisibility(View.INVISIBLE);
        else
            fileListContainer.setVisibility(View.VISIBLE);
    }

    /**
     * Create file for database
     * @return If not created, return false
     */
    private boolean createDatabaseFile(Uri path) {

        String pathString = URLDecoder.decode(path.getPath());
        // Make sure file name exists
        if (pathString.length() == 0) {
            Log.e(TAG, getString(R.string.error_filename_required));
            Toast.makeText(FileDatabaseSelectActivity.this,
                    R.string.error_filename_required,
                    Toast.LENGTH_LONG).show();
            return false;
        }

        // Try to create the file
        File file = new File(pathString);
        try {
            if (file.exists()) {
                Log.e(TAG, getString(R.string.error_database_exists) + " " + file);
                Toast.makeText(FileDatabaseSelectActivity.this,
                        R.string.error_database_exists,
                        Toast.LENGTH_LONG).show();
                return false;
            }
            File parent = file.getParentFile();

            if ( parent == null || (parent.exists() && ! parent.isDirectory()) ) {
                Log.e(TAG, getString(R.string.error_invalid_path) + " " + file);
                Toast.makeText(FileDatabaseSelectActivity.this,
                        R.string.error_invalid_path,
                        Toast.LENGTH_LONG).show();
                return false;
            }

            if ( ! parent.exists() ) {
                // Create parent directory
                if ( ! parent.mkdirs() ) {
                    Log.e(TAG, getString(R.string.error_could_not_create_parent) + " " + parent);
                    Toast.makeText(FileDatabaseSelectActivity.this,
                            R.string.error_could_not_create_parent,
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }

            return file.createNewFile();
        } catch (IOException e) {
            Log.e(TAG, getString(R.string.error_could_not_create_parent) + " " + e.getLocalizedMessage());
            e.printStackTrace();
            Toast.makeText(
                    FileDatabaseSelectActivity.this,
                    getText(R.string.error_file_not_create) + " "
                            + e.getLocalizedMessage(),
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    @Override
    public boolean onDefinePathDialogPositiveClick(Uri pathFile) {
        databaseUri = pathFile;
        if(createDatabaseFile(pathFile)) {
            AssignMasterKeyDialogFragment assignMasterKeyDialogFragment = new AssignMasterKeyDialogFragment();
            assignMasterKeyDialogFragment.show(getSupportFragmentManager(), "passwordDialog");
            return true;
        } else
            return false;
    }

    @Override
    public boolean onDefinePathDialogNegativeClick(Uri pathFile) {
        return true;
    }

    @Override
    public void onAssignKeyDialogPositiveClick(
            boolean masterPasswordChecked, String masterPassword,
            boolean keyFileChecked, Uri keyFile) {

        try {
            String databaseFilename = databaseUri.getPath();

            if (databaseFilename != null) {
                // Create the new database and start prof
                new Thread(new ProgressDialogRunnable(this,
                        R.string.progress_create,
                        progressTaskUpdater ->
                                new CreateDatabaseRunnable(databaseFilename, database -> {
                                    // TODO store database created
                                    return new AssignPasswordInDatabaseRunnable(FileDatabaseSelectActivity.this,
                                            database,
                                            masterPasswordChecked,
                                            masterPassword,
                                            keyFileChecked,
                                            keyFile,
                                            true, // TODO get readonly
                                            new LaunchGroupActivityFinish(UriUtil.parseDefaultFile(databaseFilename))
                                    );
                                })
                )).start();
            }
        } catch (Exception e) {
            String error = "Unable to create database with this password and key file";
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            Log.e(TAG, error + " " + e.getMessage());
            // TODO remove
            e.printStackTrace();
        }
    }

    private class LaunchGroupActivityFinish extends ActionRunnable {

        private Uri fileURI;

        LaunchGroupActivityFinish(Uri fileUri) {
            super();
            this.fileURI = fileUri;
        }

        @Override
        public void run() {
            finishRun(true, null);
        }

        @Override
        public void onFinishRun(boolean isSuccess, @Nullable String message) {
            runOnUiThread(() -> {
                if (isSuccess) {
                    // Add database to recent files
                    fileDatabaseHistory.addDatabaseUri(fileURI);
                    mAdapter.notifyDataSetChanged();
                    updateFileListVisibility();
                    GroupActivity.Companion.launch(FileDatabaseSelectActivity.this);
                } else {
                    Log.e(TAG, "Unable to open the database");
                }
            });
        }
    }

    @Override
    public void onAssignKeyDialogNegativeClick(
            boolean masterPasswordChecked, String masterPassword,
            boolean keyFileChecked, Uri keyFile) {

    }

    @Override
    public void onFileItemOpenListener(int itemPosition) {
        new OpenFileHistoryAsyncTask((fileName, keyFile) -> {
            launchPasswordActivity(fileName, keyFile);
            updateFileListVisibility();
        }, fileDatabaseHistory).execute(itemPosition);
    }

    @Override
    public void onClickFileInformation(FileDatabaseModel fileDatabaseModel) {
        if (fileDatabaseModel != null) {
            FileInformationDialogFragment fileInformationDialogFragment =
                    FileInformationDialogFragment.newInstance(fileDatabaseModel);
            fileInformationDialogFragment.show(getSupportFragmentManager(), "fileInformation");
        }
    }

    @Override
    public boolean onFileSelectClearListener(final FileDatabaseModel fileDatabaseModel) {
        new DeleteFileHistoryAsyncTask(() -> {
            fileDatabaseHistory.deleteDatabaseUri(fileDatabaseModel.getFileUri());
            mAdapter.notifyDataSetChanged();
            updateFileListVisibility();
        }, fileDatabaseHistory, mAdapter).execute(fileDatabaseModel);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillHelper.INSTANCE.onActivityResultSetResultAndFinish(this, requestCode, resultCode, data);
        }

        keyFileHelper.onActivityResultCallback(requestCode, resultCode, data,
                uri -> {
                    if (uri != null) {
                        if (PreferencesUtil.autoOpenSelectedFile(FileDatabaseSelectActivity.this)) {
                            launchPasswordActivityWithPath(uri.toString());
                        } else {
                            fileSelectExpandable.expand(false);
                            openFileNameView.setText(uri.toString());
                        }
                    }
                });
    }

    @OnShowRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void showRationaleForExternalStorage(final PermissionRequest request) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.permission_external_storage_rationale_write_database)
                .setPositiveButton(R.string.allow, (dialog, which) -> request.proceed())
                .setNegativeButton(R.string.cancel, (dialog, which) -> request.cancel())
                .show();
    }

    @OnPermissionDenied(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void showDeniedForExternalStorage() {
        Toast.makeText(this, R.string.permission_external_storage_denied, Toast.LENGTH_SHORT).show();
    }

    @OnNeverAskAgain(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    void showNeverAskForExternalStorage() {
        Toast.makeText(this, R.string.permission_external_storage_never_ask, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuUtil.INSTANCE.defaultMenuInflater(getMenuInflater(), menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return MenuUtil.INSTANCE.onDefaultMenuOptionsItemSelected(this, item)
                && super.onOptionsItemSelected(item);
    }
}
