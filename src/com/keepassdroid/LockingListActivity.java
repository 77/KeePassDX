/*
 * Copyright 2009 Brian Pellin.
 *     
 * This file is part of KeePassDroid.
 *
 *  KeePassDroid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  KeePassDroid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with KeePassDroid.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.keepassdroid;

import android.app.ListActivity;
import android.content.Intent;

import com.android.keepass.KeePass;
import com.keepassdroid.app.App;
import com.keepassdroid.intents.TimeoutIntents;

public class LockingListActivity extends ListActivity {

	@Override
	protected void onPause() {
		super.onPause();

		sendBroadcast(new Intent(TimeoutIntents.START));
	}

	@Override
	protected void onResume() {
		super.onResume();

		sendBroadcast(new Intent(TimeoutIntents.CANCEL));
		
		if ( App.isShutdown() ) {
			setResult(KeePass.EXIT_LOCK);
			finish();
		}
	}
}
