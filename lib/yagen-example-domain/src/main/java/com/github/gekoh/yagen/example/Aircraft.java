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

import com.github.gekoh.yagen.api.*;
import jakarta.persistence.Table;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This entity represents an aircraft
 *
 * @author Georg Kohlweiss
 */
@Entity
@Auditable
@Table(name = "AIRCRAFT")
@com.github.gekoh.yagen.api.Table(shortName = "AC")
@TemporalEntity(historyTableName = "AIRCRAFT_HST")
public class Aircraft extends BaseEntity {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Aircraft.class);

    /**
     * aircraft type
     */
    @Column(name = "TYPE", length = 20, nullable = false)
    private String type;

    /**
     * engine technology
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ENGINE_TYPE", length = Constants.ENUM_DEFAULT_LEN, nullable = false)
    private EngineType engineType;

    /**
     * national call sign
     */
    @Column(name = "CALL_SIGN", length = 7, unique = true, nullable = false)
    private String callSign;

    /**
     * horizontal dimension of aircraft from left wing tip to right wing tip
     */
    @Column(name = "WING_SPAN", precision = 5, nullable = false)
    private float wingSpan;

    /**
     * horizontal dimension of aircraft from front to tail
     */
    @Column(name = "LENGTH", columnDefinition = "numeric(5,2)", nullable = false)
    private float length;

    /**
     * maximum fuel volume
     */
    @Default(sqlExpression = "null")
    @Column(name = "MAX_FUEL_VOLUME")
    private Double fuelMaxVolume;


    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "AIRCRAFT_UUID", nullable = false)
    @CascadeDelete
    private List<BoardBookEntry> boardBookEntries;

    /**
     * raw log of production process collected while crafting the aircraft
     */
    @Lob
    @Column(name = "PRODUCTION_LOG", length = 204800)
    private String productionLog;


    Aircraft() {
    }

    public Aircraft(EngineType engineType, String type, String callSign, float wingSpan, float length, String productionLog) {
        this.engineType = engineType;
        this.type = type;
        this.callSign = callSign;
        this.wingSpan = wingSpan;
        this.length = length;
        this.boardBookEntries = new ArrayList<BoardBookEntry>();
        this.productionLog = productionLog;
    }

    public EngineType getEngineType() {
        return engineType;
    }

    public String getType() {
        return type;
    }

    public String getCallSign() {
        return callSign;
    }

    public float getWingSpan() {
        return wingSpan;
    }

    public float getLength() {
        return length;
    }

    public String getProductionLog() {
        return productionLog;
    }

    public List<BoardBookEntry> getBoardBookEntries() {
        return Collections.unmodifiableList(boardBookEntries);
    }

    public void addBoardBookEntry(BoardBookEntry boardBookEntry) {
        boardBookEntries.add(boardBookEntry);
    }

    public void removeBoardBookEntry(BoardBookEntry boardBookEntry) {
        boardBookEntries.remove(boardBookEntry);
    }
}