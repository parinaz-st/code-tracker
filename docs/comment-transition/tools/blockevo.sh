#!/bin/bash
# usage: blockevo.sh <repo> <startCommit> <file> <fixed-string pattern of first line> [occurrence=1]
# Prints, newest to oldest, each version of the file where the block (from the line containing the pattern
# to its balanced closing brace) differs from the next newer version. "//"-commented lines are uncommented
# before matching, and the state (CODE / COMMENTED / ABSENT) is shown.
repo=$1; start=$2; file=$3; pat=$4; occ=${5:-1}
cd "$repo"
prev=""; prevc=""
extract() {
  git show "$1:$2" 2>/dev/null | awk -v P="$pat" -v OCC="$occ" '
    { raw=$0; line=$0; cm=0; if (match(line, /^[ \t]*\/\//)) { sub(/^[ \t]*\/\/+/, "", line); cm=1 } }
    !on && index(line, P) { k++; if (k==OCC) { on=1; d=0; s=NR; state = cm ? "COMMENTED" : "CODE" } }
    on { t=line; n=gsub(/\{/,"{",t); m=gsub(/\}/,"}",t); d+=n-m; print (cm?"//":"  ") line; if (d<=0 && (n+m)>0) { print "@@RANGE " s "-" NR; exit } }'
}
for c in $(git log --format=%h "$start" -- "$file"); do
  full=$(extract $c "$file"); rng=$(echo "$full" | grep "^@@RANGE" | cut -c9-); blk=$(echo "$full" | grep -v "^@@RANGE")
  if [ -z "$blk" ]; then st=ABSENT; elif echo "$blk" | head -1 | grep -q '^//'; then st=COMMENTED; else st=CODE; fi
  norm=$(echo "$blk" | sed 's/^\/\///' | tr -d ' \t')
  if [ -n "$prevc" ] && { [ "$norm" != "$prev" ] || [ "$st" != "$prevst" ]; }; then
    echo "=== change at $prevc : $st [$rng] -> $prevst [$prevrng]  ($(git log -1 --format='%ct %s' $prevc | cut -c1-70))"
    diff <(echo "$blk" | sed 's/^\/\///' | sed 's/^[ \t]*//') <(echo "$prevblk" | sed 's/^\/\///' | sed 's/^[ \t]*//') | grep '^[<>]' | head -8
  fi
  prev=$norm; prevst=$st; prevc=$c; prevblk=$blk; prevrng=$rng
  [ "$st" = ABSENT ] && { echo "=== absent before $c (stop)"; break; }
done
