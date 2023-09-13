#!/bin/bash
set -e

STYLE_END="$(tput sgr0)"
STYLE_ERROR="$(tput bold)$(tput setaf 1)"
STYLE_WARN="$(tput setaf 3)"
STYLE_INFO="$(tput setaf 6)"

test -f version.properties || (echo -e "${STYLE_ERROR}You must call this script from the root folder.${STYLE_END}" && exit 1)

PLATFORM="$(uname -s)"
case "${PLATFORM}" in
  Darwin*)
    PLATFORM=mac
    BUILD_PLATFORM=darwin-x86_64
    CPU_COUNT=$(sysctl -n hw.logicalcpu_max)
    DEFAULT_ANDROID_SDK_ROOT=~/Library/Android/sdk
    SED="gsed"
    ;;
  Linux*)
    PLATFORM=linux
    BUILD_PLATFORM=linux-x86_64
    CPU_COUNT=$(lscpu -p | grep -Evc '^#')
    DEFAULT_ANDROID_SDK_ROOT=~/Android/Sdk
    SED="sed"
    ;;
  MINGW*|MSYS*)
    PLATFORM=windows
    BUILD_PLATFORM=windows-x86_64
    CPU_COUNT=$(nproc --all)
    DEFAULT_ANDROID_SDK_ROOT=~/AppData/Local/Android/Sdk
    SED="sed"
    WIN_PATCH_REQUIRED=true
    ;;
  *)
    echo -e "${STYLE_ERROR}Unsupported platform: ${PLATFORM}. Aborting.${STYLE_END}"
    exit 1
    ;;
esac

if [ ! "$IGNORE_SDK" ]; then
  if [[ ! -d ${ANDROID_SDK_ROOT} ]]; then
    if [[ "$1" == "--default-sdk-root" ]]; then
      ANDROID_SDK_ROOT=$DEFAULT_ANDROID_SDK
    else
      while true; do
        read -r -p "ANDROID_SDK_ROOT is not set. Default is ${DEFAULT_ANDROID_SDK_ROOT}. Proceed with default? [Y/n]: " yn
        case $yn in
          [Yy]* ) ANDROID_SDK_ROOT=$DEFAULT_ANDROID_SDK_ROOT; break;;
          [Nn]* ) exit;;
          * ) echo "Please answer yes or no.";;
        esac
      done
    fi
  fi

  NDK_VERSION=$(scripts/./read-property.sh version.properties version.ndk)
  CMAKE_VERSION=$(scripts/./read-property.sh version.properties version.cmake)
  if [[ ! "$NDK_VERSION" =~ ^[_0-9\.]+$ ]]; then
    echo "${STYLE_ERROR}Invalid NDK version: $NDK_VERSION!${STYLE_END}"
    exit 1
  fi

  ANDROID_NDK="$ANDROID_SDK_ROOT/ndk/$NDK_VERSION"

  CC="$ANDROID_NDK/toolchains/llvm/prebuilt/$BUILD_PLATFORM/bin/clang"
  CXX="$ANDROID_NDK/toolchains/llvm/prebuilt/$BUILD_PLATFORM/bin/clang++"
  LD="$ANDROID_NDK/toolchains/llvm/prebuilt/$BUILD_PLATFORM/bin/ld.lld"

  PATH="$ANDROID_NDK/prebuilt/$BUILD_PLATFORM/bin:$ANDROID_SDK_ROOT/cmake/$CMAKE_VERSION/bin:$ANDROID_NDK:$ANDROID_SDK_ROOT/tools/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

  (test -d "$ANDROID_SDK_ROOT" && (test -d "$ANDROID_NDK" || echo -e "${STYLE_WARN}Android NDK $NDK_VERSION is not installed.${STYLE_END}")) || echo -e "${STYLE_WARN}Android SDK is not installed.${STYLE_END}"
fi

# Export

export CPU_COUNT
export BUILD_PLATFORM

if [ ! "$IGNORE_SDK" ]; then
  export NDK_VERSION
  export CMAKE_VERSION
  export ANDROID_NDK
  export ANDROID_SDK_ROOT
  export CC
  export CXX
  export LD
fi

export STYLE_END
export STYLE_ERROR
export STYLE_WARN
export STYLE_INFO

export SED

THIRDPARTY_LIBRARIES="$(pwd)/app/jni/third_party"
export THIRDPARTY_LIBRARIES

PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$(pwd)/scripts:$(pwd)/scripts/private:$PATH"
export PATH

export WIN_PATCH_REQUIRED

function validate_file {
  test -f "$1" || (echo "File not found: $1" && false)
}
function validate_dir {
  test -d "$1" || (echo "Directory not found: $1" && false)
}

export -f validate_dir
export -f validate_file