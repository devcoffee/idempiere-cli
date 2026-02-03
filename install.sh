#!/bin/bash
set -e

# iDempiere CLI Installer
# Usage: curl -fsSL https://raw.githubusercontent.com/idempiere/idempiere-cli/main/install.sh | bash

REPO="devcoffee/idempiere-cli"
INSTALL_DIR="${INSTALL_DIR:-/usr/local/bin}"
BINARY_NAME="idempiere"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
    exit 1
}

# Detect OS
detect_os() {
    case "$(uname -s)" in
        Linux*)     echo "linux";;
        Darwin*)    echo "darwin";;
        MINGW*|MSYS*|CYGWIN*) echo "windows";;
        *)          error "Unsupported operating system: $(uname -s)";;
    esac
}

# Detect architecture
detect_arch() {
    case "$(uname -m)" in
        x86_64|amd64)   echo "amd64";;
        arm64|aarch64)  echo "arm64";;
        *)              error "Unsupported architecture: $(uname -m)";;
    esac
}

# Get latest release version from GitHub
get_latest_version() {
    curl -sL "https://api.github.com/repos/${REPO}/releases/latest" | \
        grep '"tag_name":' | \
        sed -E 's/.*"v([^"]+)".*/\1/' || echo ""
}

# Download and install
install() {
    local os=$(detect_os)
    local arch=$(detect_arch)
    local version="${VERSION:-$(get_latest_version)}"

    if [ -z "$version" ]; then
        error "Could not determine latest version. Please specify VERSION=x.x.x"
    fi

    info "Installing idempiere-cli v${version} for ${os}-${arch}..."

    local filename="idempiere-${os}-${arch}"
    if [ "$os" = "windows" ]; then
        filename="${filename}.exe"
    fi

    local url="https://github.com/${REPO}/releases/download/v${version}/${filename}"

    info "Downloading from: ${url}"

    # Create temp directory
    local tmpdir=$(mktemp -d)
    trap "rm -rf $tmpdir" EXIT

    # Download binary
    if command -v curl &> /dev/null; then
        curl -fsSL "$url" -o "${tmpdir}/${filename}" || error "Download failed. Check if the release exists."
    elif command -v wget &> /dev/null; then
        wget -q "$url" -O "${tmpdir}/${filename}" || error "Download failed. Check if the release exists."
    else
        error "Neither curl nor wget found. Please install one of them."
    fi

    # Make executable (not needed for Windows but doesn't hurt)
    chmod +x "${tmpdir}/${filename}"

    # Determine install location
    local target_dir="$INSTALL_DIR"
    local target_file="${target_dir}/${BINARY_NAME}"

    if [ "$os" = "windows" ]; then
        target_file="${target_file}.exe"
    fi

    # Check if we can write to the install directory
    if [ ! -w "$target_dir" ]; then
        warn "Cannot write to ${target_dir}, trying ~/.local/bin instead"
        target_dir="${HOME}/.local/bin"
        mkdir -p "$target_dir"
        target_file="${target_dir}/${BINARY_NAME}"
        if [ "$os" = "windows" ]; then
            target_file="${target_file}.exe"
        fi
    fi

    # Install
    info "Installing to: ${target_file}"
    mv "${tmpdir}/${filename}" "$target_file"

    # Verify installation
    if [ -x "$target_file" ]; then
        info "Installation successful!"
        echo ""

        # Check if directory is in PATH
        if [[ ":$PATH:" != *":${target_dir}:"* ]]; then
            warn "${target_dir} is not in your PATH"
            echo ""
            echo "Add it to your PATH by adding this line to your shell profile:"
            echo ""
            echo "  export PATH=\"\$PATH:${target_dir}\""
            echo ""
        fi

        echo "Run 'idempiere doctor' to verify your environment."
        echo ""
    else
        error "Installation failed"
    fi
}

# Show help
show_help() {
    echo "iDempiere CLI Installer"
    echo ""
    echo "Usage:"
    echo "  curl -fsSL https://raw.githubusercontent.com/${REPO}/main/install.sh | bash"
    echo ""
    echo "Options (via environment variables):"
    echo "  VERSION=x.x.x     Install specific version (default: latest)"
    echo "  INSTALL_DIR=/path Install to specific directory (default: /usr/local/bin)"
    echo ""
    echo "Examples:"
    echo "  # Install latest version"
    echo "  curl -fsSL https://raw.githubusercontent.com/${REPO}/main/install.sh | bash"
    echo ""
    echo "  # Install specific version"
    echo "  curl -fsSL https://raw.githubusercontent.com/${REPO}/main/install.sh | VERSION=1.0.0 bash"
    echo ""
    echo "  # Install to custom directory"
    echo "  curl -fsSL https://raw.githubusercontent.com/${REPO}/main/install.sh | INSTALL_DIR=~/.local/bin bash"
}

# Main
case "${1:-}" in
    -h|--help)
        show_help
        ;;
    *)
        echo ""
        echo "  _     _                       _                        _ _ "
        echo " (_)   | |                     (_)                      | (_)"
        echo "  _  __| | ___ _ __ ___  _ __   _  ___ _ __ ___    ___| |_ "
        echo " | |/ _\` |/ _ \\ '_ \` _ \\| '_ \\ | |/ _ \\ '__/ _ \\  / __| | |"
        echo " | | (_| |  __/ | | | | | |_) || |  __/ | |  __/ | (__| | |"
        echo " |_|\\__,_|\\___|_| |_| |_| .__/ |_|\\___|_|  \\___|  \\___|_|_|"
        echo "                        | |                                 "
        echo "                        |_|    Developer CLI for iDempiere  "
        echo ""
        install
        ;;
esac
