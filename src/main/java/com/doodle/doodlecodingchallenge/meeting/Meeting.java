package com.doodle.doodlecodingchallenge.meeting;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.doodle.doodlecodingchallenge.slot.Slot;
import com.doodle.doodlecodingchallenge.user.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "meetings")
public class Meeting implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2048)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false, unique = true)
    private Slot slot;

    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MeetingParticipant> participants = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected Meeting() {
    }

    public Meeting(UUID id, String title, String description, User organizer, Slot slot, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.organizer = organizer;
        this.slot = slot;
        this.createdAt = createdAt;
    }

    public void addParticipant(MeetingParticipant participant) {
        participant.setMeeting(this);
        this.participants.add(participant);
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public User getOrganizer() {
        return organizer;
    }

    public Slot getSlot() {
        return slot;
    }

    public List<MeetingParticipant> getParticipants() {
        return List.copyOf(participants);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
