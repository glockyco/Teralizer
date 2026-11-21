#!/usr/bin/env bash
# Pinned upstream sources of the JARVIS reconstruction. This file is *sourced* by the fixture
# preparer and the CUT-PVC capture, so both check out exactly the same commits.

CUTOFF="2017-02-01T12:02:01Z"
MATH_REPO="https://github.com/apache/commons-math.git"
MATH_REF="origin/master before $CUTOFF"
MATH_SHA="657b1b49da5ea1593dd7f950eae99a88a8ada87a"
LANG_REPO="https://github.com/apache/commons-lang.git"
LANG_REF="PR #230 base before the JARVIS-discovered fix"
LANG_SHA="857e0de49293083aae6d3e6c6b76ec0755b1d0fa"

# Census population: the 12 Apache Commons projects of JARVIS's Table 1. The paper does
# not publish its checkout revisions. The Commons Lang fix submitted from the JARVIS
# evaluation on 2017-02-01 provides the reconstruction cutoff. Each row pins the last
# default-branch commit before that instant, except commons-lang, which pins the PR base.
# Rows: <cache-name>|<repo-url>|<selection-basis>|<commit-sha>|<fixture-artifact>
CENSUS_PROJECTS="\
commons-math|$MATH_REPO|$MATH_REF|$MATH_SHA|commons-math-2017-02-01-census
commons-lang|$LANG_REPO|$LANG_REF|$LANG_SHA|commons-lang-2017-02-01-census
commons-cli|https://github.com/apache/commons-cli.git|origin/master before $CUTOFF|b486fbd33d9350b5faf27662f18841ae89128910|commons-cli-2017-02-01-census
commons-codec|https://github.com/apache/commons-codec.git|origin/master before $CUTOFF|1a4d9cc2572d220664f1b7c377cd318cd253052e|commons-codec-2017-02-01-census
commons-collections|https://github.com/apache/commons-collections.git|origin/master before $CUTOFF|3c1867e231093319b1bbbe5b0362d4063517cf24|commons-collections-2017-02-01-census
commons-configuration|https://github.com/apache/commons-configuration.git|origin/master before $CUTOFF|98ae727a34e9460ef625bc072cbbd7f2b0471127|commons-configuration-2017-02-01-census
commons-csv|https://github.com/apache/commons-csv.git|origin/master before $CUTOFF|b1e5d93a2f5309102bf09d4e2b8b9eb15ef3fc8b|commons-csv-2017-02-01-census
commons-email|https://github.com/apache/commons-email.git|origin/master before $CUTOFF|edcd565ab2e77f0ac8c95fa7200ff1a1be7f8e0c|commons-email-2017-02-01-census
commons-io|https://github.com/apache/commons-io.git|origin/master before $CUTOFF|54631643211e2dc29beaecc30e4eedf2928738d7|commons-io-2017-02-01-census
commons-jexl|https://github.com/apache/commons-jexl.git|origin/master before $CUTOFF|b212a407b74687c1f861dd0cb5cff90b9da32082|commons-jexl-2017-02-01-census
commons-pool|https://github.com/apache/commons-pool.git|origin/master before $CUTOFF|6a088d1b8a4aa343362cdf4e749b7c5fca38971b|commons-pool-2017-02-01-census
commons-text|https://github.com/apache/commons-text.git|origin/master before $CUTOFF|d24d8b257135e06fe89d6569a897c44e4466281a|commons-text-2017-02-01-census"

require_git_pin() {
  local repo_dir="$1"
  local ref="$2"
  local sha="$3"
  local url="$4"

  if [[ ! -d "$repo_dir/.git" ]]; then
    rm -rf "$repo_dir"
    git clone --no-checkout "$url" "$repo_dir"
  fi

  git -C "$repo_dir" fetch --tags --force origin
  git -C "$repo_dir" checkout --force --detach "$sha"
  local actual
  actual=$(git -C "$repo_dir" rev-parse HEAD)
  if [[ "$actual" != "$sha" ]]; then
    echo "Pin mismatch for $repo_dir ($ref): expected $sha, got $actual" >&2
    exit 1
  fi
}
