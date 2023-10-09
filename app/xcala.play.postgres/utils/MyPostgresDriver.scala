package xcala.play.postgres.utils

import com.github.tminglei.slickpg._
import com.github.tminglei.slickpg.str.PgStringSupport

trait MyPostgresProfile
    extends ExPostgresProfile
    with PgArraySupport
    with PgNetSupport
    with PgLTreeSupport
    with PgRangeSupport
    with PgHStoreSupport
    with PgSearchSupport
    with PgStringSupport {

  override val api: API
    with ArrayImplicits
    with NetImplicits
    with LTreeImplicits
    with RangeImplicits
    with HStoreImplicits
    with SearchImplicits
    with SearchAssistants
    with PgStringImplicits = new API
    with ArrayImplicits
    with NetImplicits
    with LTreeImplicits
    with RangeImplicits
    with HStoreImplicits
    with SearchImplicits
    with SearchAssistants
    with PgStringImplicits {}

}

object MyPostgresProfile extends MyPostgresProfile
