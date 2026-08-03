# OAuth Provider Setup

Ogh uses one public client ID per provider to identify the application. A client
ID is not a password and does not grant access to the maintainer's channel. Each
user signs in to their own provider account and authorizes their own channel.

Never add a client secret to the Android application. A value embedded in an APK
can be recovered from it.

## Client ID configuration

Client IDs are intentionally stored in the tracked Android resource file
`androidApp/src/main/res/values/strings.xml`:

```xml
<string name="youtube_client_id" translatable="false">YOUR_GOOGLE_CLIENT_ID</string>
<string name="twitch_client_id" translatable="false">YOUR_TWITCH_CLIENT_ID</string>
```

The official Ogh clients are configured there. No environment variables, local
secret file, or GitHub Actions secrets are needed. Forks should replace both
values with client IDs registered to their own app.

Do not put OAuth access tokens, refresh tokens, stream keys, API secrets, signing
keys, passwords, or client secrets in this file. Only the two public client IDs
belong in source control.

## Twitch

Ogh uses Twitch Device Authorization Grant for a native public client without a
backend-held client secret.

1. Enable two-factor authentication on the maintainer Twitch account.
2. Open the [Twitch Developer Console](https://dev.twitch.tv/console/apps) and
   register an application.
3. Use `Ogh` as the application name and select the broadcasting category.
4. Select **Public** as the client type. Device authorization does not send a
   redirect URI. If the console requires one, register a maintainer-controlled
   HTTPS URL rather than an application custom scheme.
5. Copy the Client ID into `twitch_client_id` in `strings.xml`. Do not copy the
   client secret into the project.

When a user connects Twitch, Ogh opens the activation URL returned by Twitch,
polls the token endpoint at Twitch's requested interval, and stores the resulting
user tokens with Android Keystore encryption. Twitch polling handles pending,
slow-down, denial and expiration.

## YouTube

1. Open the [Google Cloud Console](https://console.cloud.google.com/) and create or select the Ogh project.
2. Enable **YouTube Data API v3**.
3. Configure the OAuth consent screen, support contact, and exactly one scope:
   `https://www.googleapis.com/auth/youtube`.
4. Complete Google's OAuth app verification before distributing a build that requests YouTube management scopes.
5. Create the native/Android OAuth client for package `org.aossie.ogh` and the
   signing certificate used for the distributed build. Obtain a signing SHA-1
   with:

   ```bash
   keytool -list -v -keystore /secure/path/ogh-release.jks -alias YOUR_KEY_ALIAS
   ```

   Use the long-lived release certificate configured for the tag workflow, not
   a local debug certificate. Every published update must use that same signing
   identity.

6. Open the Android client's **Advanced settings** and enable **Custom URI
   scheme**. Google permits this compatibility setting but does not recommend
   custom schemes because another application can claim them, so it is disabled
   by default. Ogh uses PKCE to prevent an intercepted authorization code from
   being exchanged without the verifier. Without this setting Google rejects
   the request with `Custom URI scheme is not enabled for your Android client`;
   the page may appear to remain loading while it redirects to that error.
7. Copy the public Client ID into `youtube_client_id` in `strings.xml`. Android
   clients do not embed a client secret.

### Redirect requirement

Ogh returns from the browser through `org.aossie.ogh:/oauth2callback`. The
redirect scheme in `OAuthConfig.kt`, the manifest intent filter, and the Google
Android client's custom-scheme setting must all agree. Validate the complete
flow on both standard and de-Googled Android using the final signed review APK.
The manifest claims the scheme while AppAuth validates the complete redirect
URI.

Ogh uses the browser/custom-scheme path because the specification requires
authorization without Google Play services. Google's preferred Android
`AuthorizationClient` path would remove that compatibility.

Do not add `youtube.force-ssl`: every YouTube operation currently implemented by
Ogh accepts the single `youtube` scope, and Google requires least-privilege
requests. Ogh will not embed a web-client secret or silently depend on Google
Play Services.

Before submitting for verification, publish matching Ogh homepage and privacy
policy URLs, demonstrate connect/create/bind/disconnect in the review video,
and verify that disconnect removes Ogh from the Google Account permissions page.

YouTube uses `variable` resolution and frame rate, and prefers `rtmpsIngestionAddress`.
No OAuth response body or token may be logged.
