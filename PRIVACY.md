# Ogh Privacy Policy

Last updated: July 16, 2026

Ogh is a local-first, open-source Android streaming application. Ogh does not
operate an account service, advertising service, analytics service, or media
relay. Broadcast media is sent directly from the device to destinations chosen
by the user.

## Information Ogh accesses

Ogh accesses the selected screen, camera, microphone, or eligible device audio
only after the user selects that feature and grants the Android permission it
requires. Manual RTMP credentials are provided by the user. Manual destinations may use RTMP or RTMPS. RTMPS encrypts the stream and
credentials in transit; plain RTMP does not. Ogh recommends RTMPS whenever the
destination supports it.

When the user chooses **Connect YouTube**, Ogh requests the Google OAuth scope
`https://www.googleapis.com/auth/youtube`. Ogh uses this permission only to:

- identify the user's YouTube channel;
- create and configure a YouTube live broadcast and ingestion stream;
- bind that stream to the broadcast; and
- manage the broadcast lifecycle requested by the user.

Ogh does not use Google user data for advertising, analytics, profiling, credit
decisions, or training generalized artificial-intelligence models. Ogh does not
sell Google user data or transfer it to an Ogh server or another third party.
Ogh's use of information received from Google APIs adheres to the
[Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy),
including its Limited Use requirements.

## Local storage and security

OAuth access tokens, refresh tokens, channel identifiers, and manual streaming
credentials are stored only on the user's device. Sensitive values are
encrypted with AES-256-GCM using a non-exportable key in Android Keystore. Ogh
does not place tokens or stream keys in diagnostics or application logs.

Video, audio, and stream-metadata preferences are stored in private Android
DataStore files. If the user chooses a custom pause image, Ogh stores a
persistent read-only document URI so it can display that image during a stream.
The image remains with the user's document provider; Ogh does not copy or upload
it. Choosing the default logo releases Ogh's retained URI permission.

Ogh excludes its application data from Android cloud backup and device
transfer.

## Sharing and retention

Ogh sends OAuth tokens only to the provider that issued them and sends media
only to destinations enabled by the user. Locally stored provider information
is retained until the user disconnects that provider, clears Ogh's application
data, or uninstalls Ogh.

Disconnecting a provider requests revocation and then deletes Ogh's local
tokens and account information. YouTube users can also
revoke access from their
[Google Account connections page](https://myaccount.google.com/connections).

## User choices

Users can use manual RTMP without connecting a Google or Twitch account. They
can stop capture at any time, temporarily replace live video with a pause image
while audio continues, disable destinations individually, and disconnect
provider accounts from Ogh's Accounts screen.

## Changes and contact

Material policy changes will be documented in the project changelog and in this
file. Before OAuth verification, the maintainer must publish this policy on the
same verified domain used for Ogh's OAuth homepage and provide a current support
contact there.
