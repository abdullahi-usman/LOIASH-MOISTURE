package com.dahham.jugwriter

import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.room.*
import java.util.*
import kotlinx.parcelize.Parcelize

@Entity(tableName = "Jobs")
@Parcelize
data class Job(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    var date: Date = Calendar.getInstance().time,
    var w1: Float = 0f,
    var w2: Float = 0f,
    var w3: Float = 0f,
    var lastJobOperator: JobOperator = JobOperator.LOI
): Parcelable {

    var title: String = ""
    var notes: String = ""

    /*Ugly hack to get around database migration becuase of adding new fields*/
    var ext1: String = ""
    var ext2: String = ""
    var ext3: String = ""

    enum class JobOperator {

        LOI {
            override fun calculate(job: Job): Float {
                return ((job.w2 - job.w3) / (job.w2 - job.w1)) * 100f
            }
        },
        ASH {
            override fun calculate(job: Job): Float {
                return 100 - LOI.calculate(job)
            }
        },
        MOISTURE {
            override fun calculate(job: Job): Float {
                return LOI.calculate(job)
            }
        };

        abstract fun calculate(job: Job): Float
    }

    fun calculate(): Float {
        return lastJobOperator.calculate(job = this)
    }

    //Compose may be using equals in some way, so we cant override,
    //Currently we dont know how to override equals in a safe way wihtout breaking compose [job.value]
    fun sameAs(other: Any?): Boolean {
        if (other != null && other is Job && other.uid == uid
            && other.w1 == w1 && other.w2 == w2 && other.w3 == w3
            && other.lastJobOperator == lastJobOperator
        ) {
            return true
        }

        return false
    }

}

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY date DESC")
    fun getAll(): LiveData<List<Job>>

    @Query("SELECT * FROM jobs WHERE uid == :id")
    fun getJobById(id: Long): Job

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun Insert(job: Job): Long

    @Delete
    fun Delete(vararg job: Job)
}

@Database(entities = [Job::class], version = 1)
@TypeConverters(Converters::class)
abstract class WorkDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
}