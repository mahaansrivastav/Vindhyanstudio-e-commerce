#!/usr/bin/env python3
# Copyright (c) 2026 Mahan Srivastava. All rights reserved. See LICENSE.
"""
One-time move of the whole store from one Supabase project to another (e.g. Frankfurt → Mumbai).

  python3 scripts/migrate-supabase.py            # check both projects, copy nothing
  python3 scripts/migrate-supabase.py --copy     # copy database + storage, then compare

Old project: backend/.env.production.local   New project: backend/.env.mumbai.local
Copies the `public` schema (all app tables + Flyway history) with pg_dump/pg_restore and every file
in the storage buckets. The old project is only read, never changed. Refuses to overwrite a new
database that already has tables unless --force is given.
"""
import json
import os
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUCKETS = [("catalogue", True), ("private", False)]
TABLES = ["categories", "products", "product_images", "customers", "addresses", "orders", "order_items",
          "order_events", "payments", "shipments", "invoices", "invoice_counters", "notification_log", "sync_runs"]


def env(path):
    out = {}
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip().strip('"').strip("'")
    return out


def project(path, label):
    e = env(path)
    missing = [k for k in ("DB_URL", "DB_USERNAME", "DB_PASSWORD", "SUPABASE_URL", "SUPABASE_SERVICE_KEY") if not e.get(k) or "PASTE" in e.get(k, "")]
    if missing:
        sys.exit(f"{label}: fill {', '.join(missing)} in {os.path.relpath(path, ROOT)}")
    u = urllib.parse.urlparse(e["DB_URL"].removeprefix("jdbc:"))
    return {
        "label": label,
        "pg": {"PGHOST": u.hostname, "PGPORT": str(u.port or 5432), "PGDATABASE": u.path.lstrip("/") or "postgres",
               "PGUSER": e["DB_USERNAME"], "PGPASSWORD": e["DB_PASSWORD"], "PGSSLMODE": "require"},
        "url": e["SUPABASE_URL"].rstrip("/"),
        "key": e["SUPABASE_SERVICE_KEY"],
        "host": u.hostname,
    }


def psql(p, sql):
    r = subprocess.run(["psql", "-X", "-A", "-t", "-c", sql], env={**os.environ, **p["pg"]}, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"{p['label']} database: {r.stderr.strip()}")
    return r.stdout.strip()


def counts(p):
    existing = set(psql(p, "select table_name from information_schema.tables where table_schema='public'").split())
    return {t: int(psql(p, f"select count(*) from public.{t}")) if t in existing else None for t in TABLES}


def api(p, method, path, body=None, raw=None, content_type=None):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(p["url"] + "/storage/v1" + path, data=data, method=method)
    req.add_header("Authorization", "Bearer " + p["key"])
    req.add_header("apikey", p["key"])
    if content_type or body is not None:
        req.add_header("Content-Type", content_type or "application/json")
    if raw is not None:
        req.add_header("x-upsert", "true")
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read(), r.headers.get("Content-Type")


def list_files(p, bucket, prefix=""):
    files, offset = [], 0
    while True:
        body, _ = api(p, "POST", f"/object/list/{bucket}", {"prefix": prefix, "limit": 1000, "offset": offset,
                                                            "sortBy": {"column": "name", "order": "asc"}})
        items = json.loads(body)
        for it in items:
            path = f"{prefix}{it['name']}"
            if it.get("id") is None:  # folder
                files += list_files(p, bucket, path + "/")
            else:
                files.append(path)
        if len(items) < 1000:
            return files
        offset += 1000


def ensure_bucket(p, name, public):
    try:
        api(p, "POST", "/bucket", {"id": name, "name": name, "public": public})
        print(f"  created bucket {name} ({'public' if public else 'private'})")
    except urllib.error.HTTPError as e:
        if e.code not in (400, 409):
            raise
        api(p, "PUT", f"/bucket/{name}", {"public": public})


def main():
    copy, force = "--copy" in sys.argv, "--force" in sys.argv
    old = project(os.path.join(ROOT, "backend/.env.production.local"), "OLD")
    new = project(os.path.join(ROOT, "backend/.env.mumbai.local"), "NEW")
    if old["host"] == new["host"] and old["pg"]["PGUSER"] == new["pg"]["PGUSER"]:
        sys.exit("Old and new point to the same database. Check backend/.env.mumbai.local.")
    print(f"OLD  {old['pg']['PGUSER']} @ {old['host']}\nNEW  {new['pg']['PGUSER']} @ {new['host']}\n")

    before_old, before_new = counts(old), counts(new)
    has_tables = any(v is not None for v in before_new.values())
    print("Connected to both databases.")
    print(f"  old: {before_old['products']} products, {before_old['orders']} orders, {before_old['customers']} customers")
    print(f"  new: {'has app tables already' if has_tables else 'empty (good)'}")
    if not copy:
        print("\nCheck only. Run again with --copy to move the data.")
        return
    if has_tables and not force:
        sys.exit("\nThe new database already has app tables. Nothing copied. Use --force to replace them.")

    # 1. Database
    print("\n1/3 Copying database…")
    dump = os.path.join(tempfile.mkdtemp(), "vindhyan.dump")
    subprocess.run(["pg_dump", "--schema=public", "--no-owner", "--no-privileges", "-Fc", "-f", dump],
                   env={**os.environ, **old["pg"]}, check=True)
    # Every Supabase project already has the public schema (and its comment), so skip creating those.
    toc = subprocess.run(["pg_restore", "-l", dump], capture_output=True, text=True, check=True).stdout
    keep = [l for l in toc.splitlines() if not (" SCHEMA - public " in l or " COMMENT - SCHEMA public " in l)]
    listing = dump + ".list"
    open(listing, "w").write("\n".join(keep) + "\n")
    restore = ["pg_restore", "--no-owner", "--no-privileges", "--exit-on-error", "--single-transaction", "-L", listing, "-d", new["pg"]["PGDATABASE"]]
    if force:
        restore[1:1] = ["--clean", "--if-exists"]
    subprocess.run(restore + [dump], env={**os.environ, **new["pg"]}, check=True)
    os.remove(listing)
    os.remove(dump)

    # 2. Storage
    print("2/3 Copying storage files…")
    file_counts = {}
    for bucket, public in BUCKETS:
        ensure_bucket(new, bucket, public)
        try:
            files = list_files(old, bucket)
        except urllib.error.HTTPError as e:
            print(f"  {bucket}: not found in old project ({e.code}), skipped")
            continue
        for i, path in enumerate(files, 1):
            data, ctype = api(old, "GET", f"/object/{bucket}/{urllib.parse.quote(path)}")
            api(new, "POST", f"/object/{bucket}/{urllib.parse.quote(path)}", raw=data, content_type=ctype or "application/octet-stream")
            if i % 25 == 0 or i == len(files):
                print(f"  {bucket}: {i}/{len(files)}")
        file_counts[bucket] = (len(files), len(list_files(new, bucket)))

    # 3. Compare
    print("3/3 Comparing…\n")
    after_new = counts(new)
    ok = True
    print(f"  {'table':<18}{'old':>8}{'new':>8}")
    for t in TABLES:
        same = before_old[t] == after_new[t]
        ok &= same
        print(f"  {t:<18}{str(before_old[t]):>8}{str(after_new[t]):>8}  {'ok' if same else 'DIFFERENT'}")
    for bucket, (a, b) in file_counts.items():
        same = a <= b
        ok &= same
        print(f"  {'files/' + bucket:<18}{a:>8}{b:>8}  {'ok' if same else 'DIFFERENT'}")
    print("\nAll copied. Now switch Render to the new project." if ok else "\nSome counts differ. Don't switch yet.")


if __name__ == "__main__":
    main()
