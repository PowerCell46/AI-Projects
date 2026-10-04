package com.peter_gerdzhikov.twitter_api_gateway.entities.enums;

import java.util.function.BiConsumer;
import java.util.function.Function;

import com.peter_gerdzhikov.twitter_api_gateway.entities.files.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;

/**
 * The two picture pointers on a user, so upload and delete run one flow for both.
 */
public enum PictureSlot {

    PROFILE_PICTURE(User::getProfilePicture, User::setProfilePicture),

    COVER_PICTURE(User::getProfileCoverPicture, User::setProfileCoverPicture);

    private final Function<User, DbFile> reader;

    private final BiConsumer<User, DbFile> writer;

    PictureSlot(Function<User, DbFile> reader, BiConsumer<User, DbFile> writer) {
        this.reader = reader;
        this.writer = writer;
    }

    public DbFile read(User user) {
        return reader.apply(user);
    }

    public void write(User user, DbFile file) {
        writer.accept(user, file);
    }
}
