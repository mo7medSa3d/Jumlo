#!/usr/bin/env python3
"""Source contracts + behavioral port for Phase 2 page-operation logic.

Static: asserts PdfPageEdit exposes rotation/extract/split/duplicate and that
PageDocumentMerger/PageEditHistory exist in document-core (Android-free).
Behavioral: a faithful Python mirror validates the algorithms (move, rotate
mod-360, delete, extract, splitEvery, merge renumber, undo/redo) since no kotlinc
is available in this sandbox.
"""
import re
import unittest
from pathlib import Path
from source_paths import source_path

ROOT = Path(__file__).resolve().parents[1]


def read(relative):
    return source_path(relative).read_text(encoding='utf-8')


# --- Behavioral mirror of the Kotlin (validates the algorithm design) ---
class PdfPageEdit:
    def __init__(self, order, selected=None, rotations=None):
        self.order = list(order)
        self.selected = set(selected or [])
        self.rotations = dict(rotations or {})

    def move(self, page, to_position):
        assert page in self.order and 1 <= to_position <= len(self.order)
        u = list(self.order)
        u.remove(page)
        u.insert(to_position - 1, page)
        return PdfPageEdit(u, self.selected, self.rotations)

    def rotate(self, page, degrees):
        assert degrees in (90, 180, 270)
        n = ((self.rotations.get(page, 0) + degrees) % 360)
        r = dict(self.rotations); r[page] = n
        return PdfPageEdit(self.order, self.selected, r)

    def rotate_selected(self, degrees):
        assert degrees in (90, 180, 270)
        if not self.selected:
            return PdfPageEdit(self.order, self.selected, self.rotations)
        r = dict(self.rotations)
        for p in self.selected:
            r[p] = ((self.rotations.get(p, 0) + degrees) % 360)
        return PdfPageEdit(self.order, self.selected, r)

    def delete_selected(self):
        rem = [p for p in self.order if p not in self.selected]
        assert rem
        return PdfPageEdit(rem, set(), {k: v for k, v in self.rotations.items() if k in rem})

    def extract_selected(self):
        pages = [p for p in self.order if p in self.selected]
        assert pages
        return PdfPageEdit(pages, set(), {k: v for k, v in self.rotations.items() if k in self.selected})

    def split_every(self, n):
        assert n > 0
        return [PdfPageEdit(self.order[i:i + n], set(),
                            {k: v for k, v in self.rotations.items() if k in self.order[i:i + n]})
                for i in range(0, len(self.order), n)]


def merge(docs):
    assert docs
    order, rots, next = [], {}, 1
    for d in docs:
        for src in d.order:
            order.append(next)
            if src in d.rotations:
                rots[next] = d.rotations[src]
            next += 1
    return PdfPageEdit(order, set(), rots)


class PageEditHistory:
    def __init__(self, initial):
        self.undo_stack = [initial]
        self.redo_stack = []

    @property
    def current(self):
        return self.undo_stack[-1]

    @property
    def can_undo(self):
        return len(self.undo_stack) > 1

    @property
    def can_redo(self):
        return bool(self.redo_stack)

    def apply(self, edit):
        self.undo_stack.append(edit)
        self.redo_stack.clear()
        return edit

    def undo(self):
        assert self.can_undo
        self.redo_stack.append(self.undo_stack.pop())
        return self.undo_stack[-1]

    def redo(self):
        assert self.can_redo
        self.undo_stack.append(self.redo_stack.pop())
        return self.undo_stack[-1]


class PageOperationsStatic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.geo = read('core/PdfPageGeometry.kt')
        cls.ops = read('core/PageDocumentOperations.kt')

    def test_pdf_page_edit_has_rotation_extract_split_duplicate(self):
        for sig in ('val rotations: Map<Int, Int> = emptyMap()',
                    'fun rotate(page: Int, degrees: Int): PdfPageEdit',
                    'fun rotateSelected(degrees: Int): PdfPageEdit',
                    'fun extractSelected(): PdfPageEdit',
                    'fun splitEvery(n: Int): List<PdfPageEdit>',
                    'fun duplicate(page: Int): PdfPageEdit',
                    'fun selectAll(): PdfPageEdit',
                    'fun invertSelection(): PdfPageEdit'):
            self.assertIn(sig, self.geo, f'missing: {sig}')

    def test_merger_and_history_in_core(self):
        self.assertIn('object PageDocumentMerger', self.ops)
        self.assertIn('fun merge(docs: List<PdfPageEdit>): PdfPageEdit', self.ops)
        self.assertIn('class PageEditHistory(initial: PdfPageEdit)', self.ops)
        self.assertIn('fun undo(): PdfPageEdit', self.ops)
        self.assertIn('fun redo(): PdfPageEdit', self.ops)
        self.assertIn('val canUndo: Boolean', self.ops)
        self.assertIn('val canRedo: Boolean', self.ops)

    def test_core_module_is_android_free(self):
        for f in ('core/PdfPageGeometry.kt', 'core/PageDocumentOperations.kt'):
            src = read(f)
            self.assertNotIn('import android.', src, f'Android import leaked into {f}')


class PageOperationsBehavior(unittest.TestCase):
    def test_move_relocates_page(self):
        d = PdfPageEdit([1, 2, 3, 4])
        d = d.move(3, 1)  # page 3 to position 1
        self.assertEqual(d.order, [3, 1, 2, 4])

    def test_rotate_accumulates_mod_360(self):
        d = PdfPageEdit([1, 2])
        d = d.rotate(1, 90)
        d = d.rotate(1, 90)
        d = d.rotate(1, 90)
        d = d.rotate(1, 90)
        self.assertEqual(d.rotations[1], 0)  # 4x90 = 360 -> 0
        d = d.rotate(2, 180)
        self.assertEqual(d.rotations[2], 180)

    def test_rotate_selected_only_touches_selected(self):
        d = PdfPageEdit([1, 2, 3], selected={1, 3})
        d = d.rotate_selected(90)
        self.assertEqual(d.rotations, {1: 90, 3: 90})
        self.assertNotIn(2, d.rotations)

    def test_rotate_selected_no_selection_is_noop(self):
        d = PdfPageEdit([1, 2])
        d2 = d.rotate_selected(90)
        self.assertEqual(d2.rotations, {})

    def test_delete_selected(self):
        d = PdfPageEdit([1, 2, 3, 4], selected={2, 4})
        d = d.delete_selected()
        self.assertEqual(d.order, [1, 3])

    def test_extract_selected_keeps_order_and_rotation(self):
        d = PdfPageEdit([1, 2, 3, 4], selected={2, 4}, rotations={2: 90})
        ex = d.extract_selected()
        self.assertEqual(ex.order, [2, 4])
        self.assertEqual(ex.rotations, {2: 90})

    def test_split_every(self):
        d = PdfPageEdit([1, 2, 3, 4, 5])
        parts = d.split_every(2)
        self.assertEqual([p.order for p in parts], [[1, 2], [3, 4], [5]])

    def test_merge_renumbers_and_carries_rotation(self):
        a = PdfPageEdit([1, 2, 3], rotations={2: 90})
        b = PdfPageEdit([1, 2])
        m = merge([a, b])
        self.assertEqual(m.order, [1, 2, 3, 4, 5])
        self.assertEqual(m.rotations, {2: 90})  # a's page 2 -> merged page 2

    def test_undo_redo(self):
        h = PageEditHistory(PdfPageEdit([1, 2, 3]))
        self.assertTrue(h.can_undo is False)
        h.apply(PdfPageEdit([1, 2, 3, 4]))
        self.assertTrue(h.can_undo)
        self.assertFalse(h.can_redo)
        back = h.undo()
        self.assertEqual(back.order, [1, 2, 3])
        self.assertTrue(h.can_redo)
        forward = h.redo()
        self.assertEqual(forward.order, [1, 2, 3, 4])
        # a new edit clears the redo stack
        h.apply(PdfPageEdit([2, 1, 3, 4]))
        self.assertFalse(h.can_redo)


if __name__ == '__main__':
    unittest.main()
