package org.duracloud.audit;

import org.duracloud.audit.dynamodb.AuditLogItem;
import org.duracloud.common.error.DuraCloudCheckedException;

import com.amazonaws.AmazonClientException;
/**
 * 
 * @author Daniel Bernstein
 *         March 11, 2014
 *
 */
public class AuditLogWriteFailedException extends DuraCloudCheckedException {
    
    private static final long serialVersionUID = 1L;
    private AuditLogItem logItem;
    
    public AuditLogWriteFailedException(
        AmazonClientException ex, AuditLogItem logItem) {
        super(ex);
        this.logItem = logItem;
    }
    
    public AuditLogItem getLogItem() {
        return logItem;
    }
}