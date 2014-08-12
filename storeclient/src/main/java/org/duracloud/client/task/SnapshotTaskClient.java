/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.client.task;

import org.duracloud.error.ContentStoreException;
import org.duracloud.snapshot.dto.task.CompleteSnapshotTaskResult;
import org.duracloud.snapshot.dto.task.CreateSnapshotTaskResult;
import org.duracloud.snapshot.dto.task.GetRestoreTaskResult;
import org.duracloud.snapshot.dto.task.GetSnapshotContentsTaskResult;
import org.duracloud.snapshot.dto.task.GetSnapshotListTaskResult;
import org.duracloud.snapshot.dto.task.GetSnapshotTaskResult;
import org.duracloud.snapshot.dto.task.RestoreSnapshotTaskResult;

/**
 * Provides a client interface for the SnapshotStorageProvider's set of tasks.
 * These tasks are used to interact with snapshots and snapshot restorations.
 *
 * @author Bill Branan
 *         Date: 8/8/14
 */
public interface SnapshotTaskClient {

    /**
     * Begins the process of creating a snapshot by collecting the necessary
     * information and passing it down to the snapshot bridge application. Along
     * the way, the space provided is also set to read-only so that changes cannot
     * be made to the content.
     *
     * @param spaceId the ID of the space where the content to snapshot resides
     * @param description of the snapshot
     * @param userEmail address to inform when the snapshot is complete
     * @return results
     * @throws ContentStoreException on error
     */
    public CreateSnapshotTaskResult createSnapshot(String spaceId,
                                                   String description,
                                                   String userEmail)
        throws ContentStoreException;

    /**
     * Gets the status and details of a snapshot action.
     *
     * @param snapshotId the ID of the snapshot to retrieve
     * @return results
     * @throws ContentStoreException on error
     */
    public GetSnapshotTaskResult getSnapshot(String snapshotId)
        throws ContentStoreException;

    /**
     * Completes the snapshot process by cleaning up content that is no longer
     * needed now that the snapshot has been transferred successfully.
     *
     * @param spaceId the ID of the space that hosted the snapshot content
     * @return results
     * @throws ContentStoreException on error
     */
    public CompleteSnapshotTaskResult completeSnapshot(String spaceId)
        throws ContentStoreException;

    /**
     * Gets a listing of snapshots which are accessible to this account
     *
     * @return results
     * @throws ContentStoreException on error
     */
    public GetSnapshotListTaskResult getSnapshots()
        throws ContentStoreException;

    /**
     * Gets the list of content items that are contained in the snapshot. This is
     * the same as the list of content that existed in the original space at the
     * moment the snapshot was initiated.
     *
     * @param snapshotId the ID of the snapshot to retrieve
     * @param pageNumber the page number of result set pages
     * @param pageSize the maximum number of content items to include in the result set
     * @param prefix an optional prefix used to find content items
     * @return list of content items
     * @throws ContentStoreException on error
     */
    public GetSnapshotContentsTaskResult getSnapshotContents(String snapshotId,
                                                             int pageNumber,
                                                             int pageSize,
                                                             String prefix)
        throws ContentStoreException;

    /**
     * Begins the process of restoring a snapshot by creating a landing space and
     * informing the snapshot bridge application that a restore action needs to be
     * performed.
     *
     * @param snapshotId the ID of the snapshot to restore
     * @param userEmail address to inform when restoration is complete
     * @return results
     * @throws ContentStoreException on error
     */
    public RestoreSnapshotTaskResult restoreSnapshot(String snapshotId,
                                                     String userEmail)
        throws ContentStoreException;

    /**
     * Gets the status and details of a snapshot restore action based on the
     * ID of the restore.
     *
     * @param restoreId
     * @return results
     * @throws ContentStoreException on error
     */
    public GetRestoreTaskResult getRestore(Long restoreId)
        throws ContentStoreException;

    /**
     * Gets the status and details of a snapshot restore action based on the
     * ID of the original snapshot.
     *
     * @param snapshotId
     * @return results
     * @throws ContentStoreException on error
     */
    public GetRestoreTaskResult getRestoreBySnapshot(String snapshotId)
        throws ContentStoreException;

}
