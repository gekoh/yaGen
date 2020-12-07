/*
 Copyright 2014 Georg Kohlweiss

 Licensed under the Apache License, Version 2.0 (the License);
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an AS IS BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
package com.github.gekoh.yagen.api;

import javax.persistence.Column;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author Thomas Spiegl
 */
@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public abstract class AuditInfo implements Serializable {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ModificationInfo.class);

    public static final String CREATED_AT = "created_at";
    public static final String CREATED_BY = "created_by";
    public static final String LAST_MODIFIED_AT = "last_modified_at";
    public static final String LAST_MODIFIED_BY = "last_modified_by";

    /**
     * Entity was created at.
     */
    @Column(name = CREATED_AT, nullable = false)
    private LocalDateTime createdAt;

    /**
     * Entity was created by.
     */
    @Column(name = CREATED_BY, length = Constants.USER_NAME_LEN, nullable = false)
    private String createdBy;

    /**
     * Entity was last modified at.
     */
    @Column(name = LAST_MODIFIED_AT)
    private LocalDateTime lastModifiedAt;

    /**
     * Entity was last modified by.
     */
    @Column(name = LAST_MODIFIED_BY, length = Constants.USER_NAME_LEN)
    private String lastModifiedBy;

    public AuditInfo() {
    }

    public AuditInfo(String createdBy) {
        this.createdBy = createdBy;
    }

    protected abstract LocalDateTime getCurrentTime();

    protected abstract String getUserName();

    /**
     * Called by JPA container before sql insert.
     */
    public void prePersist() {
        this.createdAt = getCurrentTime();

        if (this.createdBy == null || this.createdBy.trim().length()<1) {
            this.createdBy = getUserName();
        }
    }

    /**
     * Called by JPA container before sql update.
     */
    public void preUpdate() {
        this.lastModifiedAt = getCurrentTime();
        this.lastModifiedBy = getUserName();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getLastModifiedAt() {
        return lastModifiedAt;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }
}
