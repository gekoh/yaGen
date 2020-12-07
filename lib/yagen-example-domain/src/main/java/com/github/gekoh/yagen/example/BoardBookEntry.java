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

import com.github.gekoh.yagen.api.Default;
import com.github.gekoh.yagen.api.TemporalEntity;

import javax.persistence.Basic;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Georg Kohlweiss
 */
@Entity
@Table(name = "BOARD_BOOK_ENTRY")
@com.github.gekoh.yagen.api.Table(shortName = "BBE")
@TemporalEntity(historyTableName = "BOARD_BOOK_ENTRY_HST")
public class BoardBookEntry extends BaseEntity {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(BoardBookEntry.class);

    /**
     * consecutive number, usually accumulated landing count
     */
    @Basic(optional = false)
    private int num;

    /**
     * names of crew, PIC, ev. flight instructor (FI) and pax
     */
    @Basic(optional = false)
    @Column(name = "CREW_AND_PAX")
    private String crewAndPax;

    /**
     * departure airport or airfield, 4 letter ICAO code
     */
    @Basic(optional = false)
    @Column(name = "DEPARTED_FROM", length = 4)
    private String departedFrom;

    /**
     * timestamp of departure in UTC
     */
    @Basic(optional = false)
    private LocalDateTime departed;

    /**
     * destination airport or airfield, 4 letter ICAO code
     */
    @Basic(optional = false)
    @Column(name = "LANDED_AT", length = 4)
    private String landedAt;

    /**
     * timestamp of final touchdown in UTC
     */
    @Basic(optional = false)
    @Column(name = "LANDING_TIME")
    private LocalDateTime landingTime;

    /**
     * landing count made, especially for touch and go training
     */
    @Basic(optional = false)
    @Default(sqlExpression = "1")
    private int landings;

    /**
     * holds information about added fuel or oil
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "OPERATING_RESOURCES", joinColumns = @JoinColumn(name = "BOARD_BOOK_UUID", nullable = false)
            , uniqueConstraints = @UniqueConstraint(name = "OPR_UK", columnNames = {"BOARD_BOOK_UUID", "ADDED_OPERATING_RESOURCES"})
    )
    @TemporalEntity(historyTableName = "OPERATING_RESOURCES_HST")
    @Column(name = "ADDED_OPERATING_RESOURCES", nullable = true)
    private Collection<String> addedOperatingResources;

    BoardBookEntry() {
    }

    public BoardBookEntry(int num, String crewAndPax, String departedFrom, LocalDateTime departed, String landedAt, LocalDateTime landingTime, int landings) {
        this.num = num;
        this.crewAndPax = crewAndPax;
        this.departedFrom = departedFrom;
        this.departed = departed;
        this.landedAt = landedAt;
        this.landingTime = landingTime;
        this.landings = landings;
        this.addedOperatingResources = new ArrayList<String>();
    }

    public int getNum() {
        return num;
    }

    public String getCrewAndPax() {
        return crewAndPax;
    }

    public String getDepartedFrom() {
        return departedFrom;
    }

    public LocalDateTime getDeparted() {
        return departed;
    }

    public String getLandedAt() {
        return landedAt;
    }

    public LocalDateTime getLandingTime() {
        return landingTime;
    }

    public int getLandings() {
        return landings;
    }

    public Collection<String> getAddedOperatingResources() {
        return addedOperatingResources;
    }
}