package models

case class PagedRecords[+T] (
  currentPage: Int, // zero is the first page.
  pageSize: Int,    // the number of items in one page.
  pageCount: Long,  // the number of pages.
  orderBy: OrderBy,
  records: Seq[T]
) {
  val nextPageExists = currentPage + 1 < pageCount
  val prevPageExists = currentPage > 0
}
