package com.github.gekoh.yagen.example.imp;

import com.github.gekoh.yagen.api.LayeredTablesView;
import jakarta.persistence.*;

/**
 * @author Georg Kohlweiss
 */
@Entity
@Table(name = "IMP_BOARD_BOOK_V")
@com.github.gekoh.yagen.api.Table(shortName = "IBB")
@LayeredTablesView(
        keyColumns = { "AC_CALL_SIGN", "NUMENTRY" },
        tableNamesInOrder = { "IMP_BOARD_BOOK_COR", "IMP_BOARD_BOOK_XST", "IMP_BOARD_BOOK", "IMP_BOARD_BOOK_SYN" },
        wheresInOrder = {
                "",
                "",
                "where t.AC_CALL_SIGN not in (select AC_CALL_SIGN from IMP_BOARD_BOOK_XST)",
                "where t.AC_CALL_SIGN not in (select AC_CALL_SIGN from IMP_BOARD_BOOK_XST)"
        }
)
public class ImpBoardBook {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ImpBoardBook.class);

    @Id
    private String loadId;

    @Column(name = "AC_CALL_SIGN")
    private String acCallSign;

    @Basic(optional = false)
    private int numEntry;

    @Basic(optional = false)
    private int landingNrFrom;

    @Basic(optional = false)
    private int landingNrTo;

    @Column(name = "CREW_AND_PAX")
    private String crewAndPax;

    @Basic(optional = false)
    private String departureTimestamp;

    @Basic(optional = false)
    private String landingTimestamp;

    @Basic
    private String fromAirport;

    @Basic
    private String toAirport;

    @Basic
    private String notesLine;

    protected ImpBoardBook() {
    }

    public ImpBoardBook(String loadId, String acCallSign, int numEntry, int landingNrFrom, int landingNrTo, String crewAndPax, String departureTimestamp, String landingTimestamp, String fromAirport, String toAirport, String notesLine) {
        this.loadId = loadId;
        this.acCallSign = acCallSign;
        this.numEntry = numEntry;
        this.landingNrFrom = landingNrFrom;
        this.landingNrTo = landingNrTo;
        this.crewAndPax = crewAndPax;
        this.departureTimestamp = departureTimestamp;
        this.landingTimestamp = landingTimestamp;
        this.fromAirport = fromAirport;
        this.toAirport = toAirport;
        this.notesLine = notesLine;
    }

    public String getLoadId() {
        return loadId;
    }

    public String getAcCallSign() {
        return acCallSign;
    }

    public int getNumEntry() {
        return numEntry;
    }

    public int getLandingNrFrom() {
        return landingNrFrom;
    }

    public int getLandingNrTo() {
        return landingNrTo;
    }

    public String getCrewAndPax() {
        return crewAndPax;
    }

    public String getDepartureTimestamp() {
        return departureTimestamp;
    }

    public String getLandingTimestamp() {
        return landingTimestamp;
    }

    public String getFromAirport() {
        return fromAirport;
    }

    public String getToAirport() {
        return toAirport;
    }

    public String getNotesLine() {
        return notesLine;
    }
}