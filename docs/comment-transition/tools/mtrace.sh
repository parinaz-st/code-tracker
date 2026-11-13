#!/bin/bash
# Lists the commits that changed one method or block, with git log -L on its exact line range
# (found by brace matching from the declaration line), and how many non-blank lines each commit changed.
# Independent of CodeTracker: use it to validate a history (ADR 0013).
# usage: mtrace.sh <repo> <commit> <file> <fixed string of the declaration line>
cd "$1"
read s e < <(git show "$2:$3" | awk -v P="$4" '!on && index($0,P){on=1; s=NR} on{t=$0; n=gsub(/{/,"",t); t2=$0; k=gsub(/}/,"",t2); d+=n-k; if (d<=0 && n+k>0){print s, NR; exit}}')
echo "range $s-$e"
git log -L "$s,$e:$3" --format='COMMIT %h %cs %s' "$2" | grep -v '^diff --git\|^index \|^--- \|^+++ \|^new file' | awk '/^COMMIT/{if (c) print c " :: " n " changed lines" (x ? " e.g. " x : ""); c=substr($0,8,60); n=0; x=""; next} /^[-+]/{ t=$0; gsub(/[ \t]/,"",t); if (length(t)>1) { n++; if (x=="") x=substr($0,1,70) } } END{ if (c) print c " :: " n " changed lines" (x ? " e.g. " x : "")}'
