package com.example.codenection2026_package.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.codenection2026_package.model.Biometrics;

import java.util.List;

@Dao
public interface BiometricsDao {

    @Insert
    long insert(Biometrics biometrics);

    @Query("SELECT * FROM biometrics ORDER BY date ASC")
    List<Biometrics> getAll();

    @Query("SELECT * FROM biometrics WHERE date = :date LIMIT 1")
    Biometrics findByDate(String date);

    @Query("SELECT * FROM biometrics WHERE id = :id LIMIT 1")
    Biometrics findById(long id);

    @Update
    void update(Biometrics biometrics);

    @Delete
    void delete(Biometrics biometrics);
}