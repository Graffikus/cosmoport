package com.space.service;

import com.space.controller.ShipOrder;
import com.space.exceptions.BadRequestException;
import com.space.exceptions.NotFoundException;
import com.space.model.Ship;
import com.space.model.ShipType;
import com.space.repository.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class ShipServiceImpl implements ShipService {

    @Autowired
    private Repository repository;
    private Calendar calendar = Calendar.getInstance();

    @Override
    public List<Ship> getAllShips(String name, String planet, ShipType shipType, Long after, Long before,
                                  Boolean isUsed, Double minSpeed, Double maxSpeed, Integer minCrewSize,
                                  Integer maxCrewSize, Double minRating, Double maxRating) {
        Iterable<Ship> ships = repository.findAll();
        List<Ship> shipsByParameters = new ArrayList<>();
        boolean isActual;
        for (Ship ship : ships) {
            isActual = true;
            calendar.setTime(ship.getProdDate());
            if (name != null && !ship.getName().toLowerCase().contains(name.toLowerCase())) isActual = false;
            else if (planet != null && !ship.getPlanet().toLowerCase().contains(planet.toLowerCase())) isActual = false;
            else if (shipType != null && !ship.getShipType().equals(shipType)) isActual = false;
            else if (after != null || before != null) {
                if (after == null) after = 0L;
                if (before == null) before = Long.MAX_VALUE;
                Calendar afterCalendar = Calendar.getInstance();
                afterCalendar.setTimeInMillis(after);
                Calendar beforeCalendar = Calendar.getInstance();
                beforeCalendar.setTimeInMillis(before);
                if (calendar.getTimeInMillis() <= afterCalendar.getTimeInMillis() || calendar.getTimeInMillis() >= beforeCalendar.getTimeInMillis())
                    isActual = false;
            } else if (isUsed != null && isUsed != ship.getUsed()) isActual = false;
            else if (minSpeed != null || maxSpeed != null) {
                if (minSpeed == null) minSpeed = Double.MIN_VALUE;
                if (maxSpeed == null) maxSpeed = Double.MAX_VALUE;
                if (ship.getSpeed() <= minSpeed || ship.getSpeed() >= maxSpeed) isActual = false;
            } else if (minCrewSize != null || maxCrewSize != null) {
                if (minCrewSize == null) minCrewSize = Integer.MIN_VALUE;
                if (maxCrewSize == null) maxCrewSize = Integer.MAX_VALUE;
                if (ship.getCrewSize() <= minCrewSize || ship.getCrewSize() >= maxCrewSize) isActual = false;
            } else if (minRating != null || maxRating != null) {
                if (minRating == null) minRating = Double.MIN_VALUE;
                if (maxRating == null) maxRating = Double.MAX_VALUE;
                if (ship.getRating() <= minRating || ship.getRating() >= maxRating) isActual = false;
            }
            if (isActual) shipsByParameters.add(ship);
        }
        return shipsByParameters;
    }

    @Override
    public List<Ship> pagedShips(List<Ship> ships, Integer pageNumber, Integer pageSize, ShipOrder shipOrder) {
        if (pageNumber == null) pageNumber = 0;
        if (pageSize == null) pageSize = 3;
        if (shipOrder == null) shipOrder = ShipOrder.ID;
        if (shipOrder.getFieldName().equals(ShipOrder.DATE.getFieldName()))
            return ships.stream().sorted(Comparator.comparing(Ship::getProdDate)).skip(pageNumber*pageSize)
                    .limit(pageSize).collect(Collectors.toList());
        if (shipOrder.getFieldName().equals(ShipOrder.RATING.getFieldName()))
            return ships.stream().sorted(Comparator.comparing(Ship::getRating)).skip(pageNumber*pageSize)
                    .limit(pageSize).collect(Collectors.toList());
        if (shipOrder.getFieldName().equals(ShipOrder.SPEED.getFieldName()))
            return ships.stream().sorted(Comparator.comparing(Ship::getSpeed)).skip(pageNumber*pageSize)
                    .limit(pageSize).collect(Collectors.toList());
        return ships.stream().sorted(Comparator.comparing(Ship::getId)).skip(pageNumber*pageSize)
                .limit(pageSize).collect(Collectors.toList());
    }

    @Override
    public Ship createShip(Ship ship) {
        if (ship.getProdDate() == null) throw new BadRequestException();
        calendar.setTime(ship.getProdDate());
        if (ship.getName() == null || ship.getPlanet() == null || ship.getShipType() == null ||
                ship.getProdDate() == null || ship.getSpeed() == null || ship.getCrewSize() == null
        ) throw new BadRequestException();
        calendar.setTime(ship.getProdDate());
        if (ship.getName().length() == 0 || ship.getName().length() > 50 || ship.getPlanet().length() == 0 ||
                ship.getPlanet().length() > 50 || calendar.get(Calendar.YEAR) > 3019 ||
                calendar.get(Calendar.YEAR) < 2800 || ship.getSpeed() > 0.99 ||
                ship.getSpeed() < 0.01 || ship.getCrewSize() < 1 || ship.getCrewSize() > 9999
        ) throw new BadRequestException();
        if (ship.getUsed() == null) ship.setUsed(false);
        calculateRating(ship);
        return repository.saveAndFlush(ship);
    }

    @Override
    public Ship updateShip(String id, Ship ship) {
        Long longId = idToLong(id);
        if (!repository.existsById(longId)) throw new NotFoundException();
        Ship modifiedShip = repository.findById(longId).get();
        if (ship.getName() != null) {
            if (ship.getName().length() == 0 || ship.getName().length() > 50) throw new BadRequestException();
            modifiedShip.setName(ship.getName());
        }
        if (ship.getPlanet() != null) {
            if (ship.getPlanet().length() == 0 || ship.getPlanet().length() > 50) throw new BadRequestException();
            modifiedShip.setPlanet(ship.getPlanet());
        }
        if (ship.getShipType() != null) modifiedShip.setShipType(ship.getShipType());
        if (ship.getProdDate() != null) {
            calendar.setTime(ship.getProdDate());
            if (calendar.get(Calendar.YEAR) < 2800 || calendar.get(Calendar.YEAR) > 3019) throw new BadRequestException();
            modifiedShip.setProdDate(ship.getProdDate());
        }
        if (ship.getSpeed() != null) {
            if (ship.getSpeed() < 0.01 || ship.getSpeed() > 0.99) throw new BadRequestException();
            modifiedShip.setSpeed(ship.getSpeed());
        }
        if (ship.getUsed() != null) modifiedShip.setUsed(ship.getUsed());
        if (ship.getCrewSize() != null) {
            if (ship.getCrewSize() < 1 || ship.getCrewSize() > 9999) throw new BadRequestException();
            modifiedShip.setCrewSize(ship.getCrewSize());
        }
        calculateRating(modifiedShip);
        return repository.save(modifiedShip);
    }

    @Override
    public Ship getShipById(String id) {
        return repository.findById(idToLong(id)).orElseThrow(new Supplier<NotFoundException>() {
            @Override
            public NotFoundException get() {
                return new NotFoundException();
            }
        });
    }

    @Override
    public void deleteShip(String id) {
        Long longId = idToLong(id);
        if (!repository.existsById(longId))
            throw new NotFoundException();
        repository.deleteById(longId);
    }

    private void calculateRating(Ship ship) {
        calendar.setTime(ship.getProdDate());
        BigDecimal rating = BigDecimal.valueOf(80 * ship.getSpeed() * (ship.getUsed() ? 0.5 : 1)
                / (3019 - calendar.get(Calendar.YEAR) + 1));
        ship.setRating(rating.setScale(2, RoundingMode.HALF_UP).doubleValue());
    }

    private Long idToLong(String id) {
        try {
            long idChecked = Long.parseLong(id);
            if (idChecked <= 0) throw new Exception();
            return idChecked;
        } catch (Exception e) {
            throw new BadRequestException();
        }
    }
}