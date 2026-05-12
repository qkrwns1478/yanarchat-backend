package com.yanarchat.backend.character;

import com.yanarchat.backend.common.BaseEntity;
import com.yanarchat.backend.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "characters")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Character extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "persona_description", nullable = false, columnDefinition = "TEXT")
    private String personaDescription;

    @Column(name = "speech_style", nullable = false, columnDefinition = "TEXT")
    private String speechStyle;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String personality;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String traits;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String background;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @OneToMany(mappedBy = "character", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CharacterFile> files = new ArrayList<>();

    @Builder
    public Character(User user, String name, String personaDescription, String speechStyle,
                     String personality, String traits, String background, String systemPrompt) {
        this.user = user;
        this.name = name;
        this.personaDescription = personaDescription;
        this.speechStyle = speechStyle;
        this.personality = personality;
        this.traits = traits;
        this.background = background;
        this.systemPrompt = systemPrompt;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updatePersona(String personaDescription, String speechStyle, String personality,
                               String traits, String background, String systemPrompt) {
        this.personaDescription = personaDescription;
        this.speechStyle = speechStyle;
        this.personality = personality;
        this.traits = traits;
        this.background = background;
        this.systemPrompt = systemPrompt;
    }
}