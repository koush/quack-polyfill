/*
 * Copyright (c) 2007, 2009, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */
package com.koushikdutta.quack.polyfill.fs

/**
 * Defines the standard open options.
 *
 * @since 1.7
 */
enum class StandardOpenOption {
    /**
     * Open for read access.
     */
    READ,
    /**
     * Open for write access.
     */
    WRITE,
    /**
     * If the file is opened for [.WRITE] access then bytes will be written
     * to the end of the file rather than the beginning.
     *
     *
     *  If the file is opened for write access by other programs, then it
     * is file system specific if writing to the end of the file is atomic.
     */
    APPEND,
    /**
     * If the file already exists and it is opened for [.WRITE]
     * access, then its length is truncated to 0. This option is ignored
     * if the file is opened only for [.READ] access.
     */
    TRUNCATE_EXISTING,
    /**
     * Create a new file if it does not exist.
     * This option is ignored if the [.CREATE_NEW] option is also set.
     * The check for the existence of the file and the creation of the file
     * if it does not exist is atomic with respect to other file system
     * operations.
     */
    CREATE,
    /**
     * Create a new file, failing if the file already exists.
     * The check for the existence of the file and the creation of the file
     * if it does not exist is atomic with respect to other file system
     * operations.
     */
    CREATE_NEW,
    /**
     * Delete on close. When this option is present then the implementation
     * makes a *best effort* attempt to delete the file when closed
     * by the appropriate `close` method. If the `close` method is
     * not invoked then a *best effort* attempt is made to delete the
     * file when the Java virtual machine terminates (either normally, as
     * defined by the Java Language Specification, or where possible, abnormally).
     * This option is primarily intended for use with *work files* that
     * are used solely by a single instance of the Java virtual machine. This
     * option is not recommended for use when opening files that are open
     * concurrently by other entities. Many of the details as to when and how
     * the file is deleted are implementation specific and therefore not
     * specified. In particular, an implementation may be unable to guarantee
     * that it deletes the expected file when replaced by an attacker while the
     * file is open. Consequently, security sensitive applications should take
     * care when using this option.
     *
     *
     *  For security reasons, this option may imply the [ ][LinkOption.NOFOLLOW_LINKS] option. In other words, if the option is present
     * when opening an existing file that is a symbolic link then it may fail
     * (by throwing [java.io.IOException]).
     */
    DELETE_ON_CLOSE,
    /**
     * Sparse file. When used with the [.CREATE_NEW] option then this
     * option provides a *hint* that the new file will be sparse. The
     * option is ignored when the file system does not support the creation of
     * sparse files.
     */
    SPARSE,
    /**
     * Requires that every update to the file's content or metadata be written
     * synchronously to the underlying storage device.
     *
     * @see [Synchronized I/O file integrity](package-summary.html.integrity)
     */
    SYNC,
    /**
     * Requires that every update to the file's content be written
     * synchronously to the underlying storage device.
     *
     * @see [Synchronized I/O file integrity](package-summary.html.integrity)
     */
    DSYNC
}