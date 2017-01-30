/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.graalvm.visualizer.data.serialization;

import java.io.IOException;

/**
 *
 */
public final class VersionMismatchException extends IOException {

    VersionMismatchException(String message) {
        super(message);
    }

}
