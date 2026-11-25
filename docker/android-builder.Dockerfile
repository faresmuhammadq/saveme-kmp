# Builder for the signed release APK, so this machine needs nothing but Docker and git.
# Holds the JDK, the Android SDK (with apksigner) and adb (platform-tools, for installing over USB).
# Built and used by scripts/build-apk.sh and scripts/install-apk.sh; nothing here is secret.
FROM eclipse-temurin:21-jdk-noble

# https://dl.google.com/android/repository/repository2-3.xml lists newer builds and their checksums.
ARG CMDLINE_TOOLS_BUILD=16111833
ARG CMDLINE_TOOLS_SHA256=0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8
# compileSdk (gradle/libs.versions.toml) and the build-tools that provide apksigner.
ARG ANDROID_PLATFORM=platforms;android-37.0
ARG ANDROID_BUILD_TOOLS=build-tools;37.0.0
# The host user, so files written to the mounted dist/ belong to them.
ARG UID=1000
ARG GID=1000

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/home/builder/.gradle \
    KONAN_DATA_DIR=/home/builder/.gradle/konan
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip git usbutils \
    && rm -rf /var/lib/apt/lists/*

# The base image ships an `ubuntu` user on 1000:1000; drop it so the builder can take the host's ids.
RUN (userdel -r ubuntu 2>/dev/null || true) \
    && groupadd -g "$GID" builder \
    && useradd -m -u "$UID" -g "$GID" builder

RUN mkdir -p "$ANDROID_HOME/cmdline-tools" \
    && curl -fsSLo /tmp/clt.zip "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip" \
    && echo "$CMDLINE_TOOLS_SHA256  /tmp/clt.zip" | sha256sum -c - \
    && unzip -q /tmp/clt.zip -d "$ANDROID_HOME/cmdline-tools" \
    && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
    && rm /tmp/clt.zip \
    && yes | sdkmanager --licenses >/dev/null \
    && sdkmanager --install platform-tools "$ANDROID_PLATFORM" "$ANDROID_BUILD_TOOLS" >/dev/null \
    && chown -R builder:builder "$ANDROID_HOME"

# Mount points owned by the builder, so the named volumes created over them are too:
#   ~/.gradle  Gradle caches, wrapper distributions and Kotlin/Native data (saveme-gradle-home)
#   ~/.android adb's key, so the phone's "Allow USB debugging?" is asked once (saveme-adb-keys)
#   /work      the committed tree, extracted from `git archive` on stdin
#   /dist      the output folder on the host
RUN mkdir -p /home/builder/.gradle /home/builder/.android /work /dist \
    && chown builder:builder /home/builder/.gradle /home/builder/.android /work /dist

USER builder
WORKDIR /work
