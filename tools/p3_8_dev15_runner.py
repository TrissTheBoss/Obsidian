from pathlib import Path

path = Path("tools/p3_8_dev15_impl.py")
text = path.read_text()
start_marker = 'replace_once(\n    frame,\n    "        StringBuilder out = new StringBuilder(23552);'
comment_marker = '# The previous replacement is intentionally guarded below because Java quote escaping is clearer with the exact source form.'
start = text.find(start_marker)
end = text.find(comment_marker)
if start < 0 or end < 0 or end <= start:
    raise SystemExit("could not locate temporary malformed StringBuilder replacement block")
text = text[:start] + text[end:]
path.write_text(text)
compile(text, str(path), "exec")
exec(compile(text, str(path), "exec"), {"__name__": "__main__", "__file__": str(path)})
