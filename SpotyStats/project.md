This application should emphasize self-expression and emotional reflection – showing how music fits into your life story across days, seasons, and different life phases.

web application that integrates with the Spotify API to fetch, visualize, and analyze a user's listening history in an intuitive and visually appealing way.


Login with their Spotify accounts (via OAuth 2.0 and PKCE – Proof Key for Code Exchange)
View their Spotify profile information
See their recent listening activity in a chronological view, grouped by day
Identify which songs in their listening history are liked
View statistics about the percentage of liked songs in their recent history
Visualize their listening patterns by artist using interactive charts
Toggle between different metrics (track count vs. listening time)
See detailed track information including duration and popularity scores

Handle user sessions and secure authentication
Create responsive layouts that work across different device
Add a "Sign in with Spotify" button in the application header. The button should only be visible when user is NOT authenticated.
· Upon login, users should always see a consent screen so they can choose which Spotify account to use.
· Handle OAuth callbacks correctly – take care of errors, too.
· Obtain and manage access tokens. Implement automatic token refresh. All sensitive tokens must be stored on the server (do not store access tokens in local storage).
Create a “Profile” page that displays (at least) the user’s name, email address, and profile picture.
· Immediately after a successful login, users should be redirected to their profile page.
· Create a “Listening history” page that displays information about the recent listening activity of a user. Use the “Get Recently Played Tracks” endpoint to fetch necessary data.
· Show tracks in chronological order with appropriate grouping (by day, week or month). For each task, display its name, duration, popularity, artist(s), album name and cover, and time stamp of when the track was played. You can use a card-based or list-based layout of your liking.
· Add a visual indicator for liked songs. Use the “Check User’s Saved Tracks” endpoint to fetch necessary data. Additionally, you can add “save/unsave” functionalities.
· Add statistics about the percentage of liked songs. If there are implemented “save/unsave” functionalities, ensure that the statistics are accurately updated following each action.
· Add interactive charts to visualize music consumption patterns grouped by artists. Users should be able to switch between two metrics – track count and listening time. For example, use a pie chart to display total aggregates or an area chart to show per-day trends.

Avoid slow page or data loads. Whenever content requires additional time to load, provide an appropriate loading indicator.

· Charts should render smoothly without lag

· API calls should be optimized to minimize requests

· Prioritize security – ensure no exposure of API keys in client-side code

· Strive for clean, readable, and well-documented code with consistent formatting

---

1. Frontend should be React with typescript, latest stable version, component based.
create directory frontend

2. Backend should be Java 25 Spring, latest spring version.
create directory backend