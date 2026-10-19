"""Checks dist/ipod.jar is something a CLDC 1.1 phone will actually load.

Walks real attribute structures rather than searching raw bytes: a leftover
"StackMapTable" string survives in the constant pool because we do not shrink,
so a byte search reports a problem that is not there.

    python build/verify_jar.py dist/ipod.jar
"""
import sys
import struct
import zipfile
from collections import Counter

# classes that exist on desktop Java but not on CLDC 1.1 / MIDP 2.0
FORBIDDEN = (
    "java/lang/StringBuilder", "java/util/ArrayList", "java/util/HashMap",
    "java/util/List", "java/util/Iterator", "java/lang/Iterable",
    "java/lang/invoke", "java/util/Objects", "java/util/Arrays",
    "java/io/File", "java/lang/reflect",
)


def constant_pool(b):
    count = struct.unpack(">H", b[8:10])[0]
    pool, i, n = {}, 10, 1
    while n < count:
        tag = b[i]
        if tag == 1:
            ln = struct.unpack(">H", b[i + 1:i + 3])[0]
            pool[n] = b[i + 3:i + 3 + ln].decode("utf-8", "replace")
            i += 3 + ln
        elif tag in (7, 8, 16, 19, 20): i += 3
        elif tag == 15: i += 4
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18): i += 5
        elif tag in (5, 6): i += 9; n += 1
        else: raise ValueError("bad constant tag %d" % tag)
        n += 1
    return pool, i


def attributes(b, i, pool, found, descend):
    for _ in range(struct.unpack(">H", b[i:i + 2])[0]):
        i += 2
        name = pool[struct.unpack(">H", b[i:i + 2])[0]]
        ln = struct.unpack(">I", b[i + 2:i + 6])[0]
        found[name] += 1
        if name == "Code" and descend:
            j = i + 6 + 4                      # max_stack, max_locals
            code_len = struct.unpack(">I", b[j:j + 4])[0]
            j += 4 + code_len
            j += 2 + struct.unpack(">H", b[j:j + 2])[0] * 8     # exception table
            for _ in range(struct.unpack(">H", b[j:j + 2])[0]):
                j += 2
                found["  Code/" + pool[struct.unpack(">H", b[j:j + 2])[0]]] += 1
                j += 2 + 4 + struct.unpack(">I", b[j + 2:j + 6])[0]
                j -= 2
            # fall through: we only needed the names
        i += 6 + ln
        i -= 2
    return i + 2


def parse(b):
    major = struct.unpack(">H", b[6:8])[0]
    pool, i = constant_pool(b)
    i += 6                                      # access, this, super
    i += 2 + struct.unpack(">H", b[i:i + 2])[0] * 2          # interfaces
    found = Counter()
    for _ in range(2):                          # fields, then methods
        n = struct.unpack(">H", b[i:i + 2])[0]
        i += 2
        for _ in range(n):
            i += 6                              # access, name, descriptor
            i = attributes(b, i, pool, found, True)
    attributes(b, i, pool, found, False)
    refs = [v for v in pool.values() if v.startswith(("java/", "javax/", "com/"))]
    return major, found, refs


def main(path):
    z = zipfile.ZipFile(path)
    names = z.namelist()
    majors, attrs, bad_refs = set(), Counter(), set()
    classes = [n for n in names if n.endswith(".class")]
    for n in classes:
        major, found, refs = parse(z.read(n))
        majors.add(major)
        attrs += found
        for r in refs:
            for f in FORBIDDEN:
                if r.startswith(f):
                    bad_refs.add(r)

    ok = True
    print("jar:            %s" % path)
    print("classes:        %d" % len(classes))

    print("class version:  %s %s" % (sorted(majors),
          "OK" if majors == {46} else "WRONG - CLDC needs 46"))
    ok &= majors == {46}

    sm = attrs.get("  Code/StackMap", 0)
    smt = attrs.get("  Code/StackMapTable", 0)
    print("preverified:    StackMap on %d methods, StackMapTable %d %s"
          % (sm, smt, "OK" if sm and not smt else "WRONG"))
    ok &= bool(sm) and not smt

    for a in ("NestHost", "NestMembers", "BootstrapMethods"):
        if attrs.get(a):
            print("modern attr:    %s present - WRONG" % a)
            ok = False

    stubs = [n for n in names if n.startswith(("javax/", "com/nokia/"))]
    print("stub leakage:   %s" % (", ".join(stubs[:3]) if stubs else "none - OK"))
    ok &= not stubs

    print("icon.png:       %s" % ("present" if "icon.png" in names else "MISSING"))
    ok &= "icon.png" in names

    print("non-CLDC refs:  %s" % (", ".join(sorted(bad_refs)) if bad_refs else "none - OK"))
    ok &= not bad_refs

    print("\n%s" % ("PASS - this will load on CLDC 1.1" if ok else "FAIL"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "dist/ipod.jar"))
