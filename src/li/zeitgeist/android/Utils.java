/**
 * Zeitgeist for Android
 * Copyright (C) 2012  Matthias Hecker <http://apoc.cc/>
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package li.zeitgeist.android;

/**
 * Utility functions. (package protected)
 */
class Utils {
    /**
     * Join a list of strings together.
     * 
     * Uses the seperator to join a array of strings together
     * into one that is returned.
     * 
     * @param strings array of strings
     * @param sep used as a delimiter string.
     * @return joined string
     */
    public static String join(String[] strings, String sep) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < strings.length; i++) {
            if (i != 0) {
                stringBuilder.append(sep);
            }
            stringBuilder.append(strings[i]);
        }

        return stringBuilder.toString();
    }
}

