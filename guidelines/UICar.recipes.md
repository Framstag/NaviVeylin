# UI — recipes (procedure)

The procedure layer of `guidelines/UICar.md`: the commands and measurements that produced a number.
Read beside the section its `## §N` anchor names.

## §3 Reading the car-list capacity

The library's packaged default is **6** (`androidx.car.app.R.integer.content_limit_list`, car-app
1.7.0, read out of the AAR), and `ConstraintManager.getContentLimit` returns that resource itself
when its remote call fails. Measured 2026-10-10 on this AVD's templates host
(`Automotive_Distant_Display_with_Google_Play`, `emulator-5554`, change `car-favorites-group-level`
task 1.1): **100 rows at car API level 8** — the *host's* number, not the library's floor. So neither
6 nor 100 may be read as "the car list limit": always take the query, and always leave the remainder
reachable. The query is `carContext.getCarService(ConstraintManager::class.java)
.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)`. No projection host was available in
that session, so a projected host's number is unrecorded.
