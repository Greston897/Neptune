#!/usr/bin/env python3
"""Cheap structural gate for the Kotlin sources: balanced delimiters, closed strings/comments.

There is no local Kotlin compiler in the development sandbox, so a stray brace used to cost a full
CI cycle to discover. This scans every source file character by character (correctly skipping line
and block comments, raw strings, char literals and `${...}` templates) and fails on the first
unbalanced delimiter. It is not a parser and does not replace compilation — it catches the gross
mistakes that a regex-based check misses, including `//` appearing inside a string literal.
"""
import pathlib
import sys


def scan(s: str):
    i, n = 0, len(s)
    stack = []
    line = 1
    while i < n:
        c = s[i]
        if c == "\n":
            line += 1
            i += 1
            continue
        if s.startswith("//", i):
            j = s.find("\n", i)
            i = n if j < 0 else j
            continue
        if s.startswith("/*", i):
            j = s.find("*/", i + 2)
            if j < 0:
                return f"unclosed block comment at line {line}"
            line += s.count("\n", i, j)
            i = j + 2
            continue
        if s.startswith('"""', i):
            j = s.find('"""', i + 3)
            if j < 0:
                return f"unclosed raw string at line {line}"
            line += s.count("\n", i, j)
            i = j + 3
            continue
        if c == '"':
            i += 1
            while i < n:
                if s[i] == "\\":
                    i += 2
                    continue
                if s[i] == '"':
                    i += 1
                    break
                # Templates are self-balanced; skip them wholesale, including nested strings.
                if s.startswith("${", i):
                    depth = 1
                    i += 2
                    while i < n and depth:
                        if s[i] == "{":
                            depth += 1
                        elif s[i] == "}":
                            depth -= 1
                        elif s[i] == '"':
                            i += 1
                            while i < n and s[i] != '"':
                                if s[i] == "\\":
                                    i += 1
                                i += 1
                        elif s[i] == "\n":
                            line += 1
                        i += 1
                    continue
                if s[i] == "\n":
                    return f"newline in string at line {line}"
                i += 1
            continue
        if c == "'":
            i += 1
            while i < n:
                if s[i] == "\\":
                    i += 2
                    continue
                if s[i] == "'":
                    i += 1
                    break
                i += 1
            continue
        if c in "{([":
            stack.append((c, line))
        elif c in "})]":
            pair = {"}": "{", ")": "(", "]": "["}[c]
            if not stack:
                return f"unexpected {c} at line {line}"
            op, ln = stack.pop()
            if op != pair:
                return f"mismatch {op} (line {ln}) vs {c} (line {line})"
        i += 1
    if stack:
        op, ln = stack[-1]
        return f"unclosed {op} opened at line {ln}"
    return None


def main():
    files = sorted(pathlib.Path("app/src").rglob("*.kt"))
    if not files:
        raise SystemExit("no Kotlin sources found")
    failures = []
    for p in files:
        err = scan(p.read_text())
        if err:
            failures.append(f"{p}: {err}")
    if failures:
        print("KOTLIN STRUCTURE CHECK FAILED")
        for f in failures:
            print("  -", f)
        raise SystemExit(1)
    print(f"OK: {len(files)} Kotlin sources structurally balanced")


if __name__ == "__main__":
    main()
