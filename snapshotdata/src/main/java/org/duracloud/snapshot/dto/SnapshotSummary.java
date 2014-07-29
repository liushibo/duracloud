/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.snapshot.dto;

import javax.xml.bind.annotation.XmlValue;

/**
 * @author Daniel Bernstein Date: 7/28/14
 */
public class SnapshotSummary {

    @XmlValue
    private String snapshotId;
    @XmlValue
    private String description;

    public SnapshotSummary() {}
    
    public SnapshotSummary(String snapshotId, String description) {
        super();
        this.snapshotId = snapshotId;
        this.description = description;
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}