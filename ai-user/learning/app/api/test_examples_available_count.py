"""Unit tests for /examples/available-count endpoint."""
import pytest
from unittest.mock import MagicMock, patch
from fastapi.testclient import TestClient
from app.main import app


client = TestClient(app)


def test_available_count_valid_request():
    """Test available-count with valid source and category."""
    with patch('app.api.examples.get_db') as mock_db:
        mock_cur = MagicMock()
        mock_cur.fetchone.return_value = {"cnt": 42}
        mock_conn = MagicMock()
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        mock_conn.cursor.return_value.__exit__.return_value = False
        mock_db.return_value.__enter__.return_value = mock_conn

        response = client.get("/examples/available-count?source=blind&category=MARRIED&window_days=14")

        assert response.status_code == 200
        data = response.json()
        assert data["source"] == "blind"
        assert data["category"] == "MARRIED"
        assert data["count"] == 42
        assert data["windowDays"] == 14
        assert "error" not in data or data.get("error") is None


def test_available_count_without_category():
    """Test available-count without category filter."""
    with patch('app.api.examples.get_db') as mock_db:
        mock_cur = MagicMock()
        mock_cur.fetchone.return_value = {"cnt": 100}
        mock_conn = MagicMock()
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        mock_conn.cursor.return_value.__exit__.return_value = False
        mock_db.return_value.__enter__.return_value = mock_conn

        response = client.get("/examples/available-count?source=natepan&window_days=30")

        assert response.status_code == 200
        data = response.json()
        assert data["source"] == "natepan"
        assert data["category"] is None
        assert data["count"] == 100
        assert data["windowDays"] == 30


def test_available_count_zero_inventory():
    """Test available-count returns 0 when no inventory."""
    with patch('app.api.examples.get_db') as mock_db:
        mock_cur = MagicMock()
        mock_cur.fetchone.return_value = {"cnt": 0}
        mock_conn = MagicMock()
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        mock_conn.cursor.return_value.__exit__.return_value = False
        mock_db.return_value.__enter__.return_value = mock_conn

        response = client.get("/examples/available-count?source=blind&category=FAMILY")

        assert response.status_code == 200
        data = response.json()
        assert data["count"] == 0


def test_available_count_invalid_source():
    """Test available-count with invalid source."""
    response = client.get("/examples/available-count?source=invalid_source&category=MARRIED")

    assert response.status_code == 200
    data = response.json()
    assert data["source"] == "invalid_source"
    assert data["count"] == 0
    assert "error" in data


def test_available_count_invalid_category():
    """Test available-count with invalid category."""
    response = client.get("/examples/available-count?source=blind&category=INVALID_CATEGORY")

    assert response.status_code == 200
    data = response.json()
    assert data["count"] == 0
    assert "error" in data


def test_available_count_missing_source():
    """Test available-count without required source parameter."""
    response = client.get("/examples/available-count?category=MARRIED")

    assert response.status_code == 422  # Unprocessable Entity (validation error)


def test_available_count_db_error():
    """Test available-count gracefully handles DB errors."""
    with patch('app.api.examples.get_db') as mock_db:
        mock_db.return_value.__enter__.side_effect = Exception("DB connection error")

        response = client.get("/examples/available-count?source=blind&category=MARRIED")

        assert response.status_code == 200
        data = response.json()
        assert data["count"] == 0
        assert "error" in data


def test_available_count_all_plazas():
    """Test available-count for all plaza categories with blind source."""
    plazas = ["MARRIED", "COUPLE", "WORK", "FAMILY", "FRIEND", "OTHER"]

    with patch('app.api.examples.get_db') as mock_db:
        mock_cur = MagicMock()
        # Mock different counts for each plaza
        counts = [271, 158, 172, 0, 0, 0]  # FAMILY and FRIEND have zero inventory
        mock_cur.fetchone.side_effect = [{"cnt": c} for c in counts]
        mock_conn = MagicMock()
        mock_conn.cursor.return_value.__enter__.return_value = mock_cur
        mock_conn.cursor.return_value.__exit__.return_value = False
        mock_db.return_value.__enter__.return_value = mock_conn

        for i, plaza in enumerate(plazas):
            mock_cur.fetchone.return_value = {"cnt": counts[i]}
            response = client.get(f"/examples/available-count?source=blind&category={plaza}")
            assert response.status_code == 200
            data = response.json()
            assert data["count"] == counts[i]
            if counts[i] == 0:
                assert data["source"] == "blind"
                assert data["category"] == plaza
