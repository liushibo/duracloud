/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot;

/**
 * @author Bill Branan
 *         Date: 8/8/14
 */
public class SnapshotConstants {

    public static final String CREATE_SNAPSHOT_TASK_NAME = "create-snapshot";
    public static final String COMPLETE_SNAPSHOT_TASK_NAME = "complete-snapshot";
    public static final String GET_SNAPSHOTS_TASK_NAME = "get-snapshots";
    public static final String GET_SNAPSHOT_TASK_NAME = "get-snapshot";
    public static final String GET_SNAPSHOT_CONTENTS_TASK_NAME = "get-snapshot-contents";

    public static final String RESTORE_SNAPSHOT_TASK_NAME = "restore-snapshot";
    public static final String GET_RESTORE_TASK_NAME = "get-restore";

    public static final int DEFAULT_CONTENT_PAGE_NUMBER = 0;
    public static final int MIN_CONTENT_PAGE_SIZE = 1;
    public static final int MAX_CONTENT_PAGE_SIZE = 1000;

}
