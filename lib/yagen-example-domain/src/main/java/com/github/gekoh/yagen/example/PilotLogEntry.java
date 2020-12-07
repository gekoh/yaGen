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
package com.github.gekoh.yagen.example;

import com.github.gekoh.yagen.api.TemporalEntity;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * @author Georg Kohlweiss
 */
@Entity
@Table(name = "PILOT_LOG_ENTRY")
@com.github.gekoh.yagen.api.Table(shortName = "PLE")
@TemporalEntity(historyTableName = "PILOT_LOG_ENTRY_HST")
public class PilotLogEntry extends BaseEntity {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(BoardBookEntry.class);

    @OneToOne
    @JoinColumn(name = "BOARD_BOOK_ENTRY_UUID")
    private BoardBookEntry boardBookEntry;

    /**
     * consecutive number, usually accumulated landing count
     */
    @Basic(optional = false)
    private int num;

    /**
     * names of crew, PIC, ev. flight instructor (FI) and pax
     */
    @Basic(optional = false)
    @Column(name = "PILOT_NAME")
    private String pilotName;

    /**
     * timestamp of block off in UTC
     */
    @Basic(optional = false)
    @Column(name = "BLOCK_OFF")
    private LocalDateTime blockOff;

    /**
     * timestamp of block on in UTC
     */
    @Basic(optional = false)
    @Column(name = "BLOCK_ON")
    private LocalDateTime blockOn;

    PilotLogEntry() {
    }

    public PilotLogEntry(BoardBookEntry boardBookEntry, int num, String pilotName, LocalDateTime blockOff, LocalDateTime blockOn) {
        this.boardBookEntry = boardBookEntry;
        this.num = num;
        this.pilotName = pilotName;
        this.blockOff = blockOff;
        this.blockOn = blockOn;
    }

    public BoardBookEntry getBoardBookEntry() {
        return boardBookEntry;
    }

    public int getNum() {
        return num;
    }

    public String getPilotName() {
        return pilotName;
    }

    public LocalDateTime getBlockOff() {
        return blockOff;
    }

    public LocalDateTime getBlockOn() {
        return blockOn;
    }
}