import javax.inject._

import play.api._
import play.api.http.{DefaultHttpFilters, HttpFilters}
import play.api.mvc._
import play.filters.csrf.CSRFFilter
import filters.LoginSessionFilter

@Singleton
class Filters @Inject() (
  csrfFilter: CSRFFilter,
  loginSessionFilter: LoginSessionFilter
) extends DefaultHttpFilters(csrfFilter, loginSessionFilter)
