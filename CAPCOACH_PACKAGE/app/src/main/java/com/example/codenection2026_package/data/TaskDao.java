package com.example.codenection2026_package.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.codenection2026_package.model.Task;

import java.util.List;

@Dao
public interface TaskDao {

    // Add a new task to the database
    @Insert
    long insert(Task task);

    // Get every task
    @Query("SELECT * FROM tasks ORDER BY date ASC, startTime ASC")
    List<Task> getAll();

    // Find a task by its ID
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    Task findById(long id);

    // Find tasks by their name
    @Query("SELECT * FROM tasks WHERE taskName = :taskName")
    List<Task> findByName(String taskName);

    // Find all tasks on a specific date
    @Query("SELECT * FROM tasks WHERE date = :date ORDER BY startTime ASC")
    List<Task> findByDate(String date);

    // Find all tasks belonging to a category
    @Query("SELECT * FROM tasks WHERE category_id = :categoryId ORDER BY date ASC, startTime ASC")
    List<Task> findByCategoryId(Long categoryId);

    // Find tasks based on classification
    @Query("SELECT * FROM tasks WHERE classification = :classification ORDER BY date ASC, startTime ASC")
    List<Task> findByClassification(String classification);

    // Update an existing task
    @Update
    void update(Task task);

    // Delete a task
    @Delete
    void delete(Task task);
}