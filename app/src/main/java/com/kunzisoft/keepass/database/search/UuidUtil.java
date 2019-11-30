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
package com.kunzisoft.keepass.database.search;

import com.kunzisoft.keepass.utils.DatabaseInputOutputUtils;

import java.util.UUID;

public class UuidUtil {

    public static String toHexString(UUID uuid) {
        if (uuid == null) { return null; }

        byte[] buf = DatabaseInputOutputUtils.INSTANCE.uuidToBytes(uuid);

        int len = buf.length;
        if (len == 0) { return ""; }

        StringBuilder sb = new StringBuilder();

        short bt;
        char high, low;
        for (byte b : buf) {
            bt = (short) (b & 0xFF);
            high = (char) (bt >>> 4);
            low = (char) (bt & 0x0F);
            sb.append(byteToChar(high));
            sb.append(byteToChar(low));
        }

        return sb.toString();
    }

    // Use short to represent unsigned byte
    private static char byteToChar(char bt) {
        if (bt >= 10) {
            return (char)('A' + bt - 10);
        }
        else {
            return (char)('0' + bt);
        }
    }
}
