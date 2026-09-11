package com.calpex_technologies.chama.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_attendance", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"meeting_id", "member_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MeetingAttendance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RsvpStatus rsvpStatus = RsvpStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String declineReason;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "attended")
    @Builder.Default
    private boolean attended = false;

    @Column(name = "marked_at")
    private LocalDateTime markedAt;

    public enum RsvpStatus {
        PENDING,      // Not yet responded
        ATTENDING,    // Confirmed will attend
        NOT_ATTENDING // Declined with reason
    }
}
