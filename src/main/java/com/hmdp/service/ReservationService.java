package com.hmdp.service;

import com.hmdp.mapper.ReservationMapper;
import com.hmdp.pojo.Reservation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReservationService {
    @Autowired
    private ReservationMapper reservationMapper;

    public void insert(Reservation reservation) {
        reservationMapper.insert(reservation);
    }

    public List<Reservation> findByPhone(String phone) {
        return reservationMapper.findByPhone(phone);
    }
}
