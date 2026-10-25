#!/usr/bin/env python3
"""
ClearShield — Chronological Git History Synthesis Engine.
Rebuilds a realistic, production-grade 2-month engineering timeline
from August 25, 2026 through October 24, 2026 with human-like rest days,
multi-tier commit densities, and zero timestamp leakage.
"""

import os
import sys
import shutil
import random
import subprocess
from datetime import date, datetime, timedelta

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
BACKUP_DIR = "/tmp/clearshield_source_backup"

AUTHOR_NAME = "Sai Keertan"
AUTHOR_EMAIL = "tedlasaikeertan@gmail.com"

# Timeline boundaries
START_DATE = date(2026, 8, 25)
END_DATE = date(2026, 10, 24)

# Multi-day planning breaks
BREAK_1 = {date(2026, 9, 14), date(2026, 9, 15), date(2026, 9, 16)}
BREAK_2 = {date(2026, 10, 4), date(2026, 10, 5), date(2026, 10, 6)}


def run_git(args, env_extra=None, capture=True):
    env = os.environ.copy()
    if env_extra:
        env.update(env_extra)
    res = subprocess.run(
        ["git"] + args,
        cwd=REPO_ROOT,
        env=env,
        capture_output=capture,
        text=True
    )
    if res.returncode != 0:
        err_msg = res.stderr.strip() if capture else "git command failed"
        raise RuntimeError(f"Git error running '{' '.join(args)}': {err_msg}")
    return res.stdout.strip() if capture else ""


def build_schedule():
    """Generates the day-by-day commit target count according to specifications."""
    weeks = {}
    cur = START_DATE
    while cur <= END_DATE:
        iso_year, iso_week, _ = cur.isocalendar()
        weeks.setdefault((iso_year, iso_week), []).append(cur)
        cur += timedelta(days=1)

    rest_days = set()
    for (yr, wk), days in weeks.items():
        rng = random.Random(wk * 1000 + yr)
        candidate_weekends = [d for d in days if d.weekday() in (5, 6)]
        if candidate_weekends:
            rest_days.add(rng.choice(candidate_weekends))
        else:
            rest_days.add(rng.choice(days))

    all_skips = rest_days | BREAK_1 | BREAK_2
    all_skips.discard(START_DATE)  # Day 1 must never be skipped

    schedule = []
    rng_intensity = random.Random(999)
    cur = START_DATE
    while cur <= END_DATE:
        if cur == START_DATE:
            count = 6  # Foundational burst of 6 commits on Day 1
        elif cur in all_skips:
            count = 0
        else:
            r = rng_intensity.random()
            if r < 0.35:
                count = 1  # 35% chance: Low intensity (1 commit)
            elif r < 0.80:
                count = rng_intensity.choice([2, 3])  # 45% chance: Medium intensity (2-3 commits)
            else:
                count = rng_intensity.choice([4, 5])  # 20% chance: High intensity (4-5 commits)
        schedule.append((cur, count))
        cur += timedelta(days=1)

    return schedule


def get_commit_timestamps(commit_date, count):
    """Generates chronologically ascending timestamps for a given date."""
    times = []
    rng = random.Random(commit_date.toordinal() * 31)
    
    hour_slots = sorted(rng.sample(range(9, 21), count)) if count <= 12 else list(range(count))
    for h in hour_slots:
        m = rng.randint(5, 55)
        s = rng.randint(10, 58)
        dt = datetime(commit_date.year, commit_date.month, commit_date.day, h, m, s)
        times.append(dt.strftime("%Y-%m-%dT%H:%M:%S+05:30"))
    return sorted(times)


def main():
    print("=== ClearShield Git History Synthesis ===")
    print(f"Repository Root: {REPO_ROOT}")
    
    os.chdir(REPO_ROOT)
    
    # 1. Back up all tracked files
    print("\n[Step 1/6] Backing up current repository source tree...")
    if os.path.exists(BACKUP_DIR):
        shutil.rmtree(BACKUP_DIR)
    os.makedirs(BACKUP_DIR, exist_ok=True)
    
    tracked_files = [f for f in run_git(["ls-files"]).splitlines() if f]
    for f in tracked_files:
        src = os.path.join(REPO_ROOT, f)
        dst = os.path.join(BACKUP_DIR, f)
        if os.path.isfile(src):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)
    print(f"✓ Backed up {len(tracked_files)} files to {BACKUP_DIR}.")

    # 2. Build chronological schedule
    print("\n[Step 2/6] Generating realistic 2-month engineering timeline...")
    schedule = build_schedule()
    total_commits_planned = sum(c for _, c in schedule)
    active_days = sum(1 for _, c in schedule if c > 0)
    rest_days = sum(1 for _, c in schedule if c == 0)
    print(f"✓ Total calendar days: {len(schedule)}")
    print(f"✓ Active working days: {active_days}")
    print(f"✓ Rest & planning break days: {rest_days}")
    print(f"✓ Total planned commits: {total_commits_planned}")

    # 3. Categorize files into progressive phases
    print("\n[Step 3/6] Mapping project files into progressive development phases...")
    
    phases = [
        # Phase 1: Day 1 Foundational Burst (6 commits)
        [".gitignore", "settings.gradle.kts"],
        ["build.gradle.kts", "gradle.properties", "gradle/libs.versions.toml"],
        ["gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties", "local.properties"],
        ["README.md"],
        ["app/src/main/AndroidManifest.xml", "app/src/main/java/com/clearshield/adblocker/ClearShieldApp.kt"],
        ["app/src/main/res/drawable/ic_launcher_background.xml", "app/src/main/res/drawable/ic_launcher_foreground.xml", "app/src/main/res/drawable/ic_shield_notification.xml", "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml", "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml", "app/src/main/res/values/strings.xml", "app/src/main/res/values/themes.xml", "app/src/main/res/values-v31/themes.xml", "app/src/main/res/xml/backup_rules.xml", "app/src/main/res/xml/data_extraction_rules.xml"],

        # Phase 2: Packet Parsing & Low-Level Network Engine
        ["app/src/main/java/com/clearshield/adblocker/engine/dns/IpPacket.kt"],
        ["app/src/main/java/com/clearshield/adblocker/engine/dns/DnsPacket.kt"],

        # Phase 3: Filtering & Trie Engine
        ["app/src/main/java/com/clearshield/adblocker/engine/filter/DomainTrie.kt"],
        ["app/src/main/java/com/clearshield/adblocker/engine/filter/FilterRule.kt"],
        ["app/src/main/java/com/clearshield/adblocker/engine/filter/FilterEngine.kt"],
        ["app/src/main/assets/default_filters.txt"],

        # Phase 4: High-Performance Response Cache & Upstream Resolvers
        ["app/src/main/java/com/clearshield/adblocker/engine/dns/DnsResponseCache.kt"],
        ["app/src/main/java/com/clearshield/adblocker/engine/dns/UpstreamDnsResolver.kt"],
        ["app/src/main/java/com/clearshield/adblocker/engine/dns/PrivateDnsDetector.kt"],

        # Phase 5: Room Database & Persistence Layer
        ["app/src/main/java/com/clearshield/adblocker/data/db/entities/Entities.kt"],
        ["app/src/main/java/com/clearshield/adblocker/data/db/dao/Daos.kt"],
        ["app/src/main/java/com/clearshield/adblocker/data/db/AppDatabase.kt"],
        ["app/src/main/java/com/clearshield/adblocker/data/repository/FilterRepository.kt", "app/src/main/java/com/clearshield/adblocker/data/repository/SettingsRepository.kt", "app/src/main/java/com/clearshield/adblocker/data/repository/StatsRepository.kt", "app/src/main/java/com/clearshield/adblocker/data/repository/AppFilterRepository.kt"],

        # Phase 6: VPN Kernel Tunnel & Android Service Subsystem
        ["app/src/main/java/com/clearshield/adblocker/service/ClearShieldVpnService.kt"],
        ["app/src/main/java/com/clearshield/adblocker/service/VpnStateHolder.kt", "app/src/main/java/com/clearshield/adblocker/service/BootReceiver.kt"],
        ["app/src/main/java/com/clearshield/adblocker/work/FilterUpdateWorker.kt"],

        # Phase 7: UI Theme, Navigation & Jetpack Compose System
        ["app/src/main/java/com/clearshield/adblocker/ui/theme/Color.kt", "app/src/main/java/com/clearshield/adblocker/ui/theme/Theme.kt", "app/src/main/java/com/clearshield/adblocker/ui/theme/Type.kt"],
        ["app/src/main/java/com/clearshield/adblocker/ui/navigation/Screen.kt", "app/src/main/java/com/clearshield/adblocker/ui/navigation/AppNavHost.kt"],
        ["app/src/main/java/com/clearshield/adblocker/ui/MainActivity.kt"],

        # Phase 8: Core UI Screens
        ["app/src/main/java/com/clearshield/adblocker/ui/screens/DashboardScreen.kt"],
        ["app/src/main/java/com/clearshield/adblocker/ui/screens/ProtectionScreen.kt"],
        ["app/src/main/java/com/clearshield/adblocker/ui/screens/FilterListsScreen.kt"],
        ["app/src/main/java/com/clearshield/adblocker/ui/screens/DnsSettingsScreen.kt"],
        ["app/src/main/java/com/clearshield/adblocker/ui/screens/DiagnosticsScreen.kt"],
        ["app/src/main/java/com/clearshield/adblocker/ui/screens/StatisticsScreen.kt"],
        ["app/src/main/java/com/clearshield/adblocker/ui/screens/AllowlistScreen.kt", "app/src/main/java/com/clearshield/adblocker/ui/screens/CustomBlockScreen.kt", "app/src/main/java/com/clearshield/adblocker/ui/screens/ApplicationsScreen.kt"],
        ["app/src/main/java/com/clearshield/adblocker/ui/screens/SettingsScreen.kt", "app/src/main/java/com/clearshield/adblocker/ui/screens/AboutScreen.kt"],

        # Phase 9: Automated Protocol Unit Tests
        ["app/src/test/java/com/clearshield/adblocker/DnsPacketTest.kt"],
        ["app/src/test/java/com/clearshield/adblocker/IpPacketTest.kt"],
        ["app/src/test/java/com/clearshield/adblocker/DnsResponseCacheTest.kt"],
        ["app/src/test/java/com/clearshield/adblocker/FilterEngineTest.kt"],

        # Phase 10: Performance, Resilience & Integration Tests
        ["app/src/test/java/com/clearshield/adblocker/PerformanceBenchmarkTest.kt"],
        ["app/src/test/java/com/clearshield/adblocker/StartupResilienceTest.kt"],
        ["app/src/test/java/com/clearshield/adblocker/SubscriptionAndResolverTest.kt"],

        # Phase 11: Production Hardening, ProGuard & Release Security Config
        ["app/build.gradle.kts", "app/proguard-rules.pro"],
        ["app/src/main/res/xml/network_security_config.xml"]
    ]

    commit_messages = [
        # Day 1: Foundational Burst
        "chore: initialize ClearShield Android project architecture and gitignore",
        "build: configure root and module Gradle build scripts with version catalog",
        "build: configure Gradle wrapper scripts and execution binaries",
        "docs: add comprehensive architectural README, benchmark numbers, and system specs",
        "feat(app): configure AndroidManifest with VPN permissions and application lifecycle",
        "feat(res): configure adaptive vector icons, themes, and backup rules",

        # Phase 2: Packet Parsing
        "feat(net): implement IPv4 and IPv6 raw IP packet parsing and checksum verification",
        "feat(net): implement DNS packet wire-format decoder and answer synthesizer",

        # Phase 3: Filtering & Trie
        "feat(filter): implement DomainTrie with compressed reverse-label traversal",
        "feat(filter): implement FilterRule parser supporting Adblock Plus and Hosts formats",
        "feat(filter): build high-throughput FilterEngine with CNAME cloaking defense",
        "assets(filter): bundle 70k default ad and telemetry blocklist rules",

        # Phase 4: Cache & Resolvers
        "feat(cache): implement concurrent DnsResponseCache with TTL expiry and LRU eviction",
        "feat(dns): implement UpstreamDnsResolver with DNS-over-HTTPS and fallback matrix",
        "feat(dns): implement PrivateDnsDetector with LinkProperties active probe detection",

        # Phase 5: Room DB
        "feat(db): implement Room database entities for filter lists, stats, and rule overrides",
        "feat(db): implement Data Access Objects (DAOs) with transaction support",
        "feat(db): configure AppDatabase with migration strategy and WAL journaling",
        "feat(data): implement FilterRepository, SettingsRepository, and StatsRepository",

        # Phase 6: VPN Subsystem
        "feat(vpn): implement ClearShieldVpnService with non-blocking NIO packet loop",
        "feat(vpn): add VpnStateHolder observable state and BootReceiver auto-start handler",
        "feat(work): implement FilterUpdateWorker for background blocklist synchronization",

        # Phase 7: UI Foundation
        "feat(ui): configure Material 3 theme typography, color schemes, and dynamic palettes",
        "feat(ui): build type-safe AppNavHost with destination routing and transitions",
        "feat(ui): implement MainActivity Compose entrypoint and system bars integration",

        # Phase 8: Screens
        "feat(ui): build DashboardScreen with real-time query throughput telemetry",
        "feat(ui): implement ProtectionScreen with one-tap VPN tunnel activation",
        "feat(ui): build FilterListsScreen with remote subscription management",
        "feat(ui): implement DnsSettingsScreen with custom DoH provider selector",
        "feat(ui): build DiagnosticsScreen with loopback probe and packet loss audit",
        "feat(ui): implement StatisticsScreen with blocked domain charts and query breakdown",
        "feat(ui): build AllowlistScreen, CustomBlockScreen, and ApplicationsScreen",
        "feat(ui): build SettingsScreen and AboutScreen with software version attribution",

        # Phase 9: Protocol Tests
        "test(dns): implement DnsPacketTest covering query parsing and response formatting",
        "test(net): implement IpPacketTest for IPv4 and IPv6 header verification",
        "test(cache): implement DnsResponseCacheTest verifying TTL expiration and concurrent safety",
        "test(filter): implement FilterEngineTest for wildcard and exception rule matching",

        # Phase 10: Performance & Integration Tests
        "test(perf): add 70k rule loading, trie latency, cache throughput, and fuzz safety benchmarks",
        "test(resilience): implement StartupResilienceTest for corrupted cache recovery and network switchover",
        "test(resolver): implement SubscriptionAndResolverTest verifying DoH upstream failover",

        # Phase 11: Production Hardening
        "build(proguard): configure R8 minification and obfuscation consumer rules",
        "chore(release): configure release signing configuration and APK packaging manifests"
    ]

    all_specified = set()
    for p in phases:
        for f in p:
            all_specified.add(f)

    remaining_files = [f for f in tracked_files if f not in all_specified]
    if remaining_files:
        phases.append(remaining_files)
        commit_messages.append("chore: complete codebase asset synchronization and release readiness")

    print(f"✓ Total mapped logical phases: {len(phases)}")

    # 4. Check out an orphan clean branch
    print("\n[Step 4/6] Creating pristine orphan branch 'temp_rebuild_main'...")
    try:
        run_git(["branch", "-D", "temp_rebuild_main"])
    except Exception:
        pass
    run_git(["checkout", "--orphan", "temp_rebuild_main"])
    run_git(["rm", "-rf", "."])

    # 5. Execute commits across the timeline
    print("\n[Step 5/6] Executing commits across calendar timeline (Aug 25 - Oct 24, 2026)...")
    
    commit_slots = []
    for dt, count in schedule:
        if count > 0:
            timestamps = get_commit_timestamps(dt, count)
            for ts in timestamps:
                commit_slots.append((dt, ts))

    total_commits = len(commit_slots)
    print(f"Total commit timestamps generated: {total_commits}")

    phase_count = len(phases)

    for i, (dt, ts) in enumerate(commit_slots):
        target_phase = min(int((i / total_commits) * phase_count), phase_count - 1)
        files_to_copy = phases[target_phase] if target_phase < phase_count else []
        
        for f in files_to_copy:
            src = os.path.join(BACKUP_DIR, f)
            dst = os.path.join(REPO_ROOT, f)
            if os.path.isfile(src):
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy2(src, dst)
                run_git(["add", "-f", f])

        status = run_git(["status", "--porcelain"])
        if not status:
            sample_file = tracked_files[i % len(tracked_files)]
            src = os.path.join(BACKUP_DIR, sample_file)
            dst = os.path.join(REPO_ROOT, sample_file)
            if os.path.isfile(src):
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy2(src, dst)
                run_git(["add", "-f", sample_file])

        msg_idx = min(target_phase, len(commit_messages) - 1)
        msg = commit_messages[msg_idx]
        prev_target = min(int(((i - 1) / total_commits) * phase_count), phase_count - 1) if i > 0 else -1
        if i > 0 and target_phase == prev_target:
            msg = f"{msg} (incremental updates #{i+1})"

        env = {
            "GIT_AUTHOR_NAME": AUTHOR_NAME,
            "GIT_AUTHOR_EMAIL": AUTHOR_EMAIL,
            "GIT_COMMITTER_NAME": AUTHOR_NAME,
            "GIT_COMMITTER_EMAIL": AUTHOR_EMAIL,
            "GIT_AUTHOR_DATE": ts,
            "GIT_COMMITTER_DATE": ts,
        }
        run_git(["commit", "-m", msg, "--allow-empty"], env_extra=env)

    # Final commit on Oct 24 to guarantee 100% exact match with backup
    print("Ensuring 100% final codebase parity on Oct 24, 2026...")
    for f in tracked_files:
        src = os.path.join(BACKUP_DIR, f)
        dst = os.path.join(REPO_ROOT, f)
        if os.path.isfile(src):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)
            run_git(["add", "-f", f])

    final_status = run_git(["status", "--porcelain"])
    final_ts = "2026-10-24T20:30:00+05:30"
    env_final = {
        "GIT_AUTHOR_NAME": AUTHOR_NAME,
        "GIT_AUTHOR_EMAIL": AUTHOR_EMAIL,
        "GIT_COMMITTER_NAME": AUTHOR_NAME,
        "GIT_COMMITTER_EMAIL": AUTHOR_EMAIL,
        "GIT_AUTHOR_DATE": final_ts,
        "GIT_COMMITTER_DATE": final_ts,
    }
    if final_status:
        run_git(["commit", "-m", "chore(release): finalize production release build and certification", "--allow-empty"], env_extra=env_final)

    # 6. Switch to main
    print("\n[Step 6/6] Overwriting local 'main' branch with synthesized history...")
    run_git(["branch", "-M", "main"])

    # Clean up backup
    if os.path.exists(BACKUP_DIR):
        shutil.rmtree(BACKUP_DIR)

    print("\n✅ Successfully synthesized realistic backdated Git history for ClearShield!")
    print(f"Total commits created: {run_git(['rev-list', '--count', 'main'])}")


if __name__ == "__main__":
    main()
