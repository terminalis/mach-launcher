/*
 * FLauncher
 * Copyright (C) 2021  Oscar Rojas
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.terminalis.machlauncher;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class ContentImages {
    private final ContentResolver contentResolver;

    ContentImages(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
    }

    byte[] loadContentUriImage(String contentUri) {
        try (InputStream input = contentResolver.openInputStream(Uri.parse(contentUri));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int length; (length = input.read(buffer)) != -1; ) {
                output.write(buffer, 0, length);
            }
            return output.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    List<Map<String, Object>> getMediaStoreImages() {
        List<Map<String, Object>> images = new ArrayList<>();
        try (Cursor cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media.DATA}, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            while (cursor.moveToNext()) {
                images.add(Map.of("path", cursor.getString(0)));
            }
        }
        return images;
    }
}
