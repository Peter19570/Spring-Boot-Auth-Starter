package com.example.projectname.apps.auth.model;

import com.example.projectname.apps.common.BaseEntity;
import com.example.projectname.apps.user.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "social_accounts")
public class Social extends BaseEntity {

    private String providerId;

    private String provider;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
}
