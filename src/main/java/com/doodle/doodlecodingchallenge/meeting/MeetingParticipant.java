package com.doodle.doodlecodingchallenge.meeting;

import java.util.UUID;

import com.doodle.doodlecodingchallenge.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "meeting_participants",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_participant_meeting_email",
        columnNames = {"meeting_id", "email"}))
public class MeetingParticipant {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    protected MeetingParticipant() {
    }

    public MeetingParticipant(UUID id, String displayName, String email, User user) {
        this.id = id;
        this.displayName = displayName;
        this.email = email;
        this.user = user;
    }

    void setMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public User getUser() {
        return user;
    }
}
