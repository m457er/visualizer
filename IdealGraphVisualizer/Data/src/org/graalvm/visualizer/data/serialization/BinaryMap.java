/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package org.graalvm.visualizer.data.serialization;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import org.graalvm.visualizer.settings.Settings;

public final class BinaryMap implements PreferenceChangeListener {
    private static final BinaryMap INSTANCE = new BinaryMap();

    private Map<String,String> map;

    private BinaryMap() {
        Settings.get().addPreferenceChangeListener(this);
    }
    
    public static Map<String,String> obfuscationMap() {
        return INSTANCE.map();
    }

    private Map<String,String> map() {
        if (map != null) {
            return map;
        }
        String mapFile = Settings.get().get(Settings.MAP, "");
        if (!mapFile.isEmpty()) {
            File file = new File(mapFile);
            if (file.exists()) {
                try {
                    Map<String,String> map = new HashMap<>();
                    for (String line : Files.readAllLines(file.toPath())) {
                        if (line.startsWith(" ")) {
                            continue;
                        }
                        if (line.endsWith(":")) {
                            line = line.substring(0, line.length() - 1);
                        }
                        int arrow = line.indexOf("->");
                        if (arrow == -1) {
                            continue;
                        }
                        String before = line.substring(0, arrow).trim();
                        String after = line.substring(arrow + 2, line.length()).trim();
                        map.put(after, before);
                    }
                    return this.map = map;
                } catch (IOException ex) {
                    Logger.getLogger(BinaryMap.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return this.map = Collections.emptyMap();
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent evt) {
        if (evt.getKey().equals(Settings.MAP)) {
            map = null;
        }
    }
}
