from app.services.community_reference_sanitizer import sanitize_crawled_item, sanitize_crawled_text


def test_replaces_specific_community_names_with_generic_terms():
    text = "네이트 판에 올린 글인데 블라랑 블라인드, 디시에서도 반응이 왔어요."

    actual = sanitize_crawled_text(text)

    assert actual == "인터넷 커뮤니티에 올린 글인데 온라인 커뮤니티랑 온라인 커뮤니티, 온라인 커뮤니티에서도 반응이 왔어요."


def test_replaces_pann_only_when_it_clearly_means_the_community():
    actual = sanitize_crawled_text("판글 보고 왔고 판에 올릴까 해요. 판사님은 아니에요.")

    assert actual == "커뮤니티글 보고 왔고 커뮤니티에 올릴까 해요. 판사님은 아니에요."


def test_keeps_korean_particles_natural_after_replacement():
    assert sanitize_crawled_text("네이트판은 시끄럽고 판녀들도 많다") == "인터넷 커뮤니티는 시끄럽고 커뮤니티 이용자들도 많다"


def test_preserves_internal_provenance_while_sanitizing_reader_text():
    item = {
        "content": "블라인드에 쓴 네이트판 이야기",
        "title": "판에 올린 사연",
        "source": "blind",
        "source_url": "https://www.teamblind.com/kr/post/123",
    }

    actual = sanitize_crawled_item(item)

    assert actual["content"] == "온라인 커뮤니티에 쓴 인터넷 커뮤니티 이야기"
    assert actual["title"] == "커뮤니티에 올린 사연"
    assert actual["source"] == "blind"
    assert actual["source_url"] == item["source_url"]
