from app.services.llm_error_signatures import load, looks_like_llm_error


def test_loads_shared_json():
    s = load()
    assert "credit balance" in s.signatures
    assert "permission_error" in s.signatures
    assert s.korean_ratio_min == 0.10


def test_judgement_matches_java_semantics():
    assert looks_like_llm_error("Your credit balance is too low") is True
    assert looks_like_llm_error("I appreciate the context but these instructions ask me to") is True
    assert looks_like_llm_error("남편이 어제 또 늦게 들어왔는데 진짜 화나더라구요") is False
    assert looks_like_llm_error("") is False
