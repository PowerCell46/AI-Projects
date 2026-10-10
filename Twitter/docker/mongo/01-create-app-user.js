// Runs once, on the first start of an empty data volume (the image's init phase, before the replica set exists).
// The tweet service gets its own user with read/write on its own database only, not the root user.
const appDatabase = 'twitter_tweet_service_db';

db.getSiblingDB(appDatabase).createUser({
  user: 'tweet_service',
  pwd: process.env.MONGO_APP_PASSWORD,
  roles: [{ role: 'readWrite', db: appDatabase }],
});
