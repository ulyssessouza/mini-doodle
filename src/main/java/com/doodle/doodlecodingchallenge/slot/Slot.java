package com.doodle.doodlecodingchallenge.slot;

import java.time.Instant;
import java.util.UUID;

import com.doodle.doodlecodingchallenge.meeting.Meeting;
import com.doodle.doodlecodingchallenge.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "slots")
public class Slot {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "start_at", nullable = false)
    private Instant startsAt;

    @Column(name = "end_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SlotStatus status;

    @Version
    private long version;

    @OneToOne(fetch = FetchType.LAZY, mappedBy = "slot")
    private Meeting meeting;

    protected Slot() {
    }

    public Slot(UUID id, User owner, Instant startsAt, Instant endsAt) {
        this.id = id;
        this.owner = owner;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = SlotStatus.FREE;
    }

    public void setTimes(Instant startsAt, Instant endsAt) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public void setStatus(SlotStatus status) {
        this.status = status;
    }

    public void linkMeeting(Meeting meeting) {
        this.meeting = meeting;
        this.status = SlotStatus.BUSY;
    }

    public void unlinkMeeting() {
        this.meeting = null;
        this.status = SlotStatus.FREE;
    }

    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public SlotStatus getStatus() {
        return status;
    }

    public Meeting getMeeting() {
        return meeting;
    }
}
