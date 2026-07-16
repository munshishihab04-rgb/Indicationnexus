# 01 — Android Project Setup

## Recommended Project Configuration

```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'kotlin-kapt'
}

android {
    namespace 'com.personal.agent'
    compileSdk 35

    defaultConfig {
        applicationId 'com.personal.agent'
        minSdk 26
        targetSdk 35
        versionCode 1
        versionName '1.0.0'
    }
}
```

## Recommended Dependencies

```gradle
dependencies {
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.8.4'

    implementation 'androidx.work:work-runtime-ktx:2.9.1'

    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    kapt 'androidx.room:room-compiler:2.6.1'

    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.squareup.retrofit2:retrofit:2.11.0'
    implementation 'com.squareup.retrofit2:converter-moshi:2.11.0'
    implementation 'com.squareup.moshi:moshi-kotlin:1.15.1'

    implementation 'com.google.mlkit:text-recognition:16.0.1'

    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.2.1'
}
```

## Manifest Core Permissions

Declare only permissions needed for enabled modules.

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />

<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
```

Add SMS/calls/contacts only if the product truly needs them and the user understands why:

```xml
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
```

## Required Components

```xml
<application ...>

    <activity
        android:name=".ui.MainActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>

    <service
        android:name=".core.AgentForegroundService"
        android:exported="false"
        android:foregroundServiceType="dataSync" />

    <service
        android:name=".notifications.AgentNotificationListener"
        android:label="Personal Agent Notifications"
        android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
        android:exported="true">
        <intent-filter>
            <action android:name="android.service.notification.NotificationListenerService" />
        </intent-filter>
    </service>

    <service
        android:name=".accessibility.AgentAccessibilityService"
        android:label="Personal Agent Accessibility"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
        android:exported="true">
        <intent-filter>
            <action android:name="android.accessibilityservice.AccessibilityService" />
        </intent-filter>
        <meta-data
            android:name="android.accessibilityservice"
            android:resource="@xml/accessibility_service_config" />
    </service>

    <receiver
        android:name=".scheduler.BootReceiver"
        android:exported="false">
        <intent-filter>
            <action android:name="android.intent.action.BOOT_COMPLETED" />
        </intent-filter>
    </receiver>

</application>
```

## Foreground Service Rule

The agent must run visible foreground work through a foreground service notification. Do not hide persistent user-visible indicators. Use a low-importance channel for routine sync, but keep it visible and truthful.

## Setup UI Requirements

The first-run UI should show:

- server URL or account enrollment status;
- device ID;
- enabled modules;
- required permissions;
- direct buttons to Android settings screens;
- last heartbeat;
- last config fetch;
- queue status;
- stop/pause controls.
