from teralizer.eval.reports._widening import WIDENING_REFUSAL_SQL


def test_widening_refusal_order_is_total_and_collation_stable():
    order_by = WIDENING_REFUSAL_SQL.rsplit("ORDER BY", 1)[1].strip()

    assert order_by == 'refusals DESC, code COLLATE "C"'
