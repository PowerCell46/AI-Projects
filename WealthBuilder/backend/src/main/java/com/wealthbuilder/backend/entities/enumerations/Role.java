package com.wealthbuilder.backend.entities.enumerations;


/**
 * Authorization roles. Regular accounts are {@link #USER}; {@link #MODERATOR}
 * additionally manages the asset catalog. Stored as a string in the database.
 */
public enum Role {

    USER,

    MODERATOR
}
