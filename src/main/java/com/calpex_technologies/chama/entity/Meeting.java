package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "meetings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Meeting extends TenantAwareEntity {

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String venue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MeetingType type = MeetingType.REGULAR;

    @Column(name = "invite_all")
    @Builder.Default
    private Boolean inviteAll = true; // true = all members, false = selective invites (use wrapper to handle NULL)

    @Column(name = "attendees_count")
    @Builder.Default
    private int attendeesCount = 0;

    @Column(name = "total_members")
    @Builder.Default
    private int totalMembers = 0;

    @Column(name = "quorum_met")
    @Builder.Default
    private boolean quorumMet = false;

    @Column(name = "fines_collected", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal finesCollected = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "minutes_status")
    @Builder.Default
    private MinutesStatus minutesStatus = MinutesStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String agenda;

    @Column(columnDefinition = "TEXT")
    private String minutes;

    @Column(name = "minutes_file_path")
    private String minutesFilePath;  // Path to uploaded minutes document

    @Column(name = "minutes_file_name")
    private String minutesFileName;  // Original file name

    @Column(name = "minutes_uploaded_at")
    private java.time.LocalDateTime minutesUploadedAt;

    // Cascade delete attendance records when meeting is deleted
    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MeetingAttendance> attendances = new ArrayList<>();

    // Cascade delete fines when meeting is deleted
    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Fine> fines = new ArrayList<>();

    public enum MeetingType {
        REGULAR, AGM, SPECIAL
    }

    public enum MinutesStatus {
        DRAFT, CONFIRMED
    }
}
