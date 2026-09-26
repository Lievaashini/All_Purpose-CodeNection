package com.example.codenection2026_package.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.codenection2026_package.model.Category;

import java.util.List;

@Dao
public interface CategoryDao {

    @Insert
    long insert(Category category);

    @Query("SELECT * FROM categories ORDER BY name ASC")
    List<Category> getAll();

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    Category findByName(String name);

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    Category findById(long id);

    @Delete
    void delete(Category category);
}